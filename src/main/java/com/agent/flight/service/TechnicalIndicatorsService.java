package com.agent.flight.service;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class TechnicalIndicatorsService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TechnicalIndicatorsService() { }

    public record Candle(LocalDate date, BigDecimal close) { }
    public record Result(String symbol, String source, LocalDate requestedFrom, LocalDate requestedTo,
                         LocalDate historyFrom, LocalDate asOf, int observations, BigDecimal lastClose,
                         int movingAveragePeriod, BigDecimal sma, BigDecimal ema,
                         int rsiPeriod, BigDecimal rsi, List<String> notes) { }

    public static String fromJson(String symbol, String json, int maPeriod, int rsiPeriod,
                                  LocalDate from, LocalDate to) {
        try {
            var data = JSON.readTree(json).get("data");
            if (data == null || !data.isArray()) return "Erro: formato de histórico inválido no StockData.";
            var candles = new ArrayList<Candle>();
            for (var item : data) {
                var dateNode = item.get("date");
                var closeNode = item.has("close") ? item.get("close") : item.path("data").get("close");
                if (dateNode == null || !dateNode.isString() || closeNode == null || !closeNode.isNumber()) {
                    return "Erro: histórico contém data ou fechamento ausente/inválido.";
                }
                if (item.hasNonNull("ticker") && !symbol.equalsIgnoreCase(item.get("ticker").asText())) {
                    return "Erro: histórico contém um ativo diferente do solicitado.";
                }
                LocalDate date = LocalDate.parse(dateNode.asText().split("T", 2)[0]);
                if (date.isBefore(from) || date.isAfter(to)) continue;
                candles.add(new Candle(date, closeNode.decimalValue()));
            }
            if (candles.isEmpty()) return "Erro: nenhum fechamento encontrado no intervalo solicitado.";
            return JSON.writeValueAsString(calculate(symbol, candles, maPeriod, rsiPeriod, from, to));
        } catch (RuntimeException exception) {
            // Nunca devolva o corpo bruto de erros da API nem detalhes internos ao modelo.
            return "Erro: histórico inválido; verifique datas, fechamentos positivos e pregões duplicados.";
        }
    }

    public static Result calculate(String symbol, List<Candle> history, int maPeriod, int rsiPeriod,
                                   LocalDate from, LocalDate to) {
        if (maPeriod < 2 || maPeriod > 250 || rsiPeriod < 2 || rsiPeriod > 250 || history.isEmpty()) {
            throw new IllegalArgumentException("Períodos ou histórico inválidos");
        }
        var sorted = history.stream().sorted(Comparator.comparing(Candle::date)).toList();
        var dates = new HashSet<LocalDate>();
        for (var candle : sorted) {
            if (candle.close() == null || candle.close().signum() <= 0 || !dates.add(candle.date())) {
                throw new IllegalArgumentException("Fechamento inválido ou pregão duplicado");
            }
        }
        var prices = sorted.stream().map(Candle::close).toList();
        var notes = new ArrayList<String>();
        BigDecimal sma = null;
        BigDecimal ema = null;
        BigDecimal rsi = null;
        if (prices.size() >= maPeriod) {
            sma = mean(prices.subList(prices.size() - maPeriod, prices.size()));
            ema = mean(prices.subList(0, maPeriod));
            BigDecimal alpha = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(maPeriod + 1), MC);
            for (int i = maPeriod; i < prices.size(); i++) {
                ema = ema.add(prices.get(i).subtract(ema, MC).multiply(alpha, MC), MC);
            }
        } else {
            notes.add("SMA/EMA indisponíveis: necessários " + maPeriod + " fechamentos, recebidos " + prices.size() + ".");
        }
        if (prices.size() > rsiPeriod) {
            BigDecimal gain = BigDecimal.ZERO;
            BigDecimal loss = BigDecimal.ZERO;
            BigDecimal period = BigDecimal.valueOf(rsiPeriod);
            for (int i = 1; i <= rsiPeriod; i++) {
                BigDecimal delta = prices.get(i).subtract(prices.get(i - 1), MC);
                gain = gain.add(delta.max(BigDecimal.ZERO), MC);
                loss = loss.add(delta.negate().max(BigDecimal.ZERO), MC);
            }
            gain = gain.divide(period, MC);
            loss = loss.divide(period, MC);
            for (int i = rsiPeriod + 1; i < prices.size(); i++) {
                BigDecimal delta = prices.get(i).subtract(prices.get(i - 1), MC);
                gain = gain.multiply(period.subtract(BigDecimal.ONE), MC)
                        .add(delta.max(BigDecimal.ZERO), MC).divide(period, MC);
                loss = loss.multiply(period.subtract(BigDecimal.ONE), MC)
                        .add(delta.negate().max(BigDecimal.ZERO), MC).divide(period, MC);
            }
            if (gain.signum() == 0 && loss.signum() == 0) {
                rsi = BigDecimal.valueOf(50);
                notes.add("RSI de série constante convencionado em 50 (sem ganhos nem perdas).");
            } else if (loss.signum() == 0) {
                rsi = BigDecimal.valueOf(100);
            } else {
                rsi = BigDecimal.valueOf(100).subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(gain.divide(loss, MC), MC), MC), MC);
            }
        } else {
            notes.add("RSI indisponível: necessários " + (rsiPeriod + 1) + " fechamentos, recebidos " + prices.size() + ".");
        }
        notes.add("Série diária EOD ajustada por splits; períodos em pregões. Não é cotação em tempo real.");
        notes.add("EMA inicializada pela SMA; RSI usa suavização de Wilder. Valores dependem do histórico retornado e dos limites do plano.");
        return new Result(symbol, "StockData /v1/data/eod", from, to, sorted.getFirst().date(),
                sorted.getLast().date(), prices.size(), prices.getLast(), maPeriod, rounded(sma), rounded(ema),
                rsiPeriod, rounded(rsi), List.copyOf(notes));
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                .divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static BigDecimal rounded(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
