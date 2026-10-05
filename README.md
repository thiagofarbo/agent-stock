# Agent Flight — pesquisa de ações

O `StockService` usa o agente `StockSearch`, com a ferramenta `StockTools.search`
registrada no LangChain4j. A ferramenta recebe símbolos separados por vírgula
(por exemplo, `TSLA,MSFT`) e consulta `GET https://api.stockdata.org/v1/data/quote`.

## Configuração

Configure o token do StockData no ambiente do processo Java (ou nas variáveis de
ambiente da configuração de execução da IDE):

```sh
export STOCKDATA_API_TOKEN='seu-token-stockdata'
./mvnw spring-boot:run
```

A propriedade `stockdata.api-token` lê essa variável. O token é incluído no
parâmetro `api_token` da requisição, sem ser passado ao modelo. A configuração do
modelo existente em `cv.openai.*` também é necessária para executar o agente.

Injete `StockService` e chame, por exemplo:

```java
stockService.search("Consulte as cotações de TSLA e MSFT");
```

O agente recebe o JSON da API com preços, moeda, variação diária, capitalização
quando disponível e horário da última negociação. Valores ausentes não devem ser
inventados. O endpoint cobre ações de bolsas dos EUA; não fornece cotações de
todas as bolsas, histórico ou notícias.

A ferramenta informa erros de configuração, autenticação, limite de consultas e
conectividade. As conexões têm timeout de 5 segundos e a leitura de 10 segundos.

Documentação: https://www.stockdata.org/documentation

## Testes

```sh
./mvnw test
```

Os testes da ferramenta simulam a API, sem consumir créditos do StockData ou
chamar o modelo.

## Redis e cache semântico

Inicie o Redis local:

```sh
docker compose up -d --wait redis
docker compose exec redis redis-cli ping
```

O `compose.yaml` usa Redis 8.2.1 com busca vetorial e JSON, necessários ao
`RedisEmbeddingStore`. A porta padrão é `127.0.0.1:6379`. Os dados são persistidos
no volume `redis-data`, com AOF habilitado. Este Compose é para desenvolvimento
local, sem senha e com a porta publicada apenas no loopback.

A classe `SemanticCache` pode ser injetada em um serviço Spring e usada assim:

```java
return semanticCache.getOrCompute(pergunta, () -> agente.search(pergunta));
```

O cache gera o embedding da pergunta, busca uma resposta com similaridade mínima
configurada e só executa o `Supplier` quando não encontra um resultado. Ao gravar,
a chave vetorial representa a **pergunta** e o texto armazenado é a **resposta**.
Respostas nulas, vazias e exceções do gerador não são armazenadas. Falhas de Redis
ou de embeddings são propagadas ao chamador.

O `StockService.search` chama a sobrecarga `getOrCompute(pergunta, ttl, gerador)`.
Ela exige pergunta exatamente igual e validade de 60 segundos (`STOCK_CACHE_TTL`)
para reutilizar a resposta. Perguntas com ativos, datas ou períodos diferentes
não reutilizam respostas apenas pela similaridade. A expiração é lógica; a entrada
vencida é removida quando reencontrada, sem limpeza periódica dos demais registros.
O método sem TTL permanece disponível para conteúdo estável.
O bean é criado sob demanda, mas pesquisar ações exige Redis disponível.
O índice e prefixo padrão foram versionados para não reaproveitar o cache antigo.
Se você personalizou esses valores, use um novo índice e prefixo nesta versão.

Configuração por variáveis de ambiente:

| Variável | Padrão |
| --- | --- |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `SEMANTIC_CACHE_INDEX` | `semantic-cache-stock-v2-1536` |
| `SEMANTIC_CACHE_PREFIX` | `semantic-cache:stock-v2-1536:` |
| `SEMANTIC_CACHE_EMBEDDING_MODEL` | `text-embedding-3-small` |
| `SEMANTIC_CACHE_EMBEDDING_DIMENSION` | `1536` |
| `SEMANTIC_CACHE_MIN_SCORE` | `0.9` |

Os embeddings usam a chave já configurada em `cv.openai.api-key` e fazem uma
chamada OpenAI por consulta ao cache, inclusive em hits. Ao mudar o modelo ou a
dimensão, configure **outro índice e outro prefixo**; vetores de modelos diferentes
não devem ser misturados. Use um prefixo exclusivo terminado em `:`.

Para uma aplicação em outro container da mesma rede Compose, use `REDIS_HOST=redis`
e a porta interna `6379`. Para parar o Redis preservando o volume:

```sh
docker compose down
```

Teste opcional com Redis real (a geração de embeddings é simulada):

```sh
./mvnw -Dtest=SemanticCacheRedisTests -Dredis.integration=true -Dredis.test.port=6379 test
```

Referências: [Redis no LangChain4j](https://docs.langchain4j.dev/integrations/embedding-stores/redis/)
e [Redis no Docker](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/docker/).


## Indicadores técnicos

O agente atual ganhou a ferramenta `StockTools.technicalIndicators`, registrada
junto com as ferramentas de cotações e notícias. Não é necessário outro endpoint:

```sh
curl --get 'http://localhost:8080/api/stocks/search' \
  --data-urlencode 'search=Calcule SMA e EMA de 20 pregões e RSI de 14 pregões da TSLA'
```

Também é possível pedir datas e períodos específicos:

> Analise MSFT com SMA e EMA de 50 pregões e RSI de 14, usando o histórico de 2025-01-01 a 2025-12-31.

A ferramenta consulta `/v1/data/eod` (fechamentos diários ajustados por splits) e
`TechnicalIndicators` calcula em Java:

- SMA: média dos últimos N fechamentos.
- EMA: primeira média inicializada com SMA(N), seguida por suavização com fator 2/(N+1).
- RSI: médias iniciais dos ganhos/perdas de N variações, seguidas por suavização de Wilder.
  São necessários N+1 fechamentos. Sem perdas, RSI=100; sem ganhos, RSI=0;
  para série constante, adotamos RSI=50 e informamos essa convenção no resultado.

Os padrões são médias de 20 pregões, RSI de 14 e histórico dos 365 dias anteriores
até ontem (UTC). Períodos aceitos: 2 a 250. Não incluímos o dia corrente para evitar
usar um fechamento ainda incompleto. O histórico retornado pode ser menor por
limites do plano, feriados ou disponibilidade do ativo. EMA e RSI dependem da
janela usada para inicialização e podem diferir de outras plataformas.

O resultado inclui fonte, datas solicitadas e efetivas, quantidade de observações,
último fechamento e indicadores. Se faltarem dados, o indicador fica nulo com uma
explicação. Dados inválidos e pregões duplicados são rejeitados; não se inventam
fechamentos para feriados. Para múltiplos ativos ou períodos de médias, o agente
faz chamadas separadas. A interpretação não deve tratar RSI como ordem automática
nem afirmar cruzamentos apenas com valores pontuais.

Referências de cálculo: [EMA — Fidelity](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/ema)
e [RSI — StockCharts](https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/relative-strength-index-rsi).
