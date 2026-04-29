# Explicacao Do Poupador

Este arquivo documenta a versao final do agente `Poupador` em [src/agente/Poupador.java](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java).

O objetivo aqui e duplo:

1. explicar de forma didatica como o agente funciona como sistema de decisao;
2. explicar as linhas e os metodos principais do codigo.

## 1. Visao Geral

O agente toma uma decisao a cada tick da simulacao.

As acoes possiveis sao 5:

- `PARADO`
- `CIMA`
- `BAIXO`
- `DIREITA`
- `ESQUERDA`

Cada uma dessas acoes passa pelo mesmo pipeline:

1. admissibilidade;
2. heuristicas;
3. conversao de score em peso probabilistico;
4. sorteio por roleta.

Em outras palavras: o agente nao escolhe por `if/else` puro nem por aleatoriedade pura. Ele primeiro elimina acoes impossiveis ou suicidas, depois pontua as restantes e por fim sorteia proporcionalmente ao peso.

Nesta versao, as heuristicas principais foram reorganizadas para trabalhar com valores normalizados em `0..1`.
Isso deixa os pesos mais interpretaveis: eles representam distribuicao de importancia, e nao "forca arbitraria".

## 2. Fluxo Principal

O fluxo principal esta no metodo `acao()` em [src/agente/Poupador.java:47](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:47).

Ele faz 5 coisas:

1. le a posicao atual do agente;
2. atualiza o contador de permanencia na mesma celula;
3. registra visita da celula;
4. monta um `Contexto` com tudo que importa naquele tick;
5. calcula scores, converte para pesos e sorteia a acao final.

Resumo conceitual:

```text
sensores -> contexto -> scores -> pesos -> roleta -> acao final
```

## 3. O Que E O Contexto

O `Contexto` comeca em [src/agente/Poupador.java:331](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:331).

Ele empacota:

- `visao`
- `olfatoLadrao`
- `posicao`
- `numeroMoedas`
- `numeroJogadasImunes`
- `lugaresVisitados`
- `permanenciasConsecutivas`

Na pratica, ele representa o "estado do mundo" que o agente conhece naquele instante.

## 4. Como O Score De Cada Acao E Montado

O metodo central e `calcularScores()` em [src/agente/Poupador.java:80](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:80).

Para cada acao:

1. chama `avaliarAdmissibilidade()`;
2. se a acao for proibida, recebe `-INF`;
3. se for permitida, soma:
   - seguranca
   - banco
   - moeda
   - pastilha
   - estagnacao

Formula conceitual:

```text
score(acao) =
    admissibilidade
  + seguranca
  + banco
  + moeda
  + pastilha
  + estagnacao
```

## 5. Admissibilidade

O metodo `avaliarAdmissibilidade()` esta em [src/agente/Poupador.java:105](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:105).

Uma acao recebe `-INF` se:

- o destino nao for valido;
- a acao representar captura imediata evitavel.

### Exemplo concreto

Se o log mostra:

```text
-> ESQUERDA score=-INF ... valido=true captEv=true
```

isso significa:

- a celula em si nao e proibida;
- mas ir para la termina em ameaca imediata de ladrão, existindo alternativa admissivel mais segura;
- por isso a acao foi bloqueada.

## 6. Heuristica De Seguranca

O metodo `heuristicaSeguranca()` esta em [src/agente/Poupador.java:117](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:117).

Ele usa:

- risco visual
- risco olfativo

Formula:

```text
seguranca = -(0.30 * riscoVisualNorm) - (0.15 * riscoOlfativoNorm)
```

Quanto maior o risco, mais negativo fica o score.

### Exemplo concreto

Se uma acao tem:

```text
rVisNorm=0.500
rOlfNorm=0.000
```

entao a parcela de seguranca fica:

```text
seg = -(0.30 * 0.5) - (0.15 * 0.0) = -0.150
```

O valor absoluto caiu bastante em relacao a versao antiga, porque agora o objetivo e manter custo e beneficio em escalas comparaveis.

### Observacao importante

Nesta versao, a imunidade nao participa mais do calculo do risco visual.

Ou seja, o risco visual bruto ficou conceitualmente assim:

```text
rvis(a) = soma_i [ 1 / max(1, d(a, li)) ]
```

Isso simplifica a explicacao matematica da heuristica e deixa a seguranca mais conservadora: o agente passa a tratar estados imunes e nao imunes com o mesmo risco visual heuristico.

## 7. Heuristica Do Banco

O metodo `heuristicaBanco()` esta em [src/agente/Poupador.java:129](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:129).

Ela depende de:

- urgencia financeira;
- objetivo de banco da acao.

Formula:

```text
banco = 0.22 * urgenciaBanco * objetivoBanco
```

Onde:

- `urgenciaBanco` esta em `0..1`;
- `objetivoBanco` esta em `0..1`.

`objetivoBanco` funciona assim:

- se a acao realmente deposita, vale `1.0`;
- caso contrario, vale a media entre:
  - progresso em direcao ao banco;
  - proximidade local do banco.

### Importante

A heuristica do banco foi alinhada ao motor real do jogo.

Hoje o bonus de deposito em [src/agente/Poupador.java:355](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:355) so vale quando:

- a acao aponta para a celula que o motor identifica como banco;
- o agente tem moedas.

Isso corrige a diferenca entre:

- "estar perto geometricamente do banco";
- "estar mirando a celula banco de verdade".

### Exemplo concreto

Se:

- `urgenciaBanco = 0.8`
- a acao realmente deposita

entao:

```text
ban = 0.22 * 0.8 * 1.0 = 0.176
```

Ou seja, depositar continua sendo uma acao forte, mas agora em escala compativel com as demais heuristicas.

## 8. Heuristica De Moeda

O metodo `heuristicaMoeda()` esta em [src/agente/Poupador.java:137](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:137).

Formula:

```text
moeda = 0.18 * atracaoVisivelMoedaNorm
```

Ela fica mais forte quando:

- ha moedas visiveis;
- a acao esta mais alinhada com essas moedas do que as alternativas.

Nesta versao, a heuristica de moeda foi simplificada para medir apenas atracao por moedas visiveis.

Ou seja:

- risco continua sendo responsabilidade da heuristica de seguranca;
- urgencia de retorno continua sendo responsabilidade da heuristica do banco;
- a heuristica de moeda mede apenas o quanto a acao aponta para moedas.

A atracao visivel da moeda e normalizada pela melhor opcao do tick. Assim:

- a melhor acao para moeda recebe valor `1.0`;
- as outras recebem um valor proporcional em `0..1`;
- se nao houver moeda visivel, a heuristica vira `0`.

## 9. Heuristica De Pastilha

O metodo `heuristicaPastilha()` esta em [src/agente/Poupador.java:141](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:141).

Formula:

```text
pastilha = 0.08 * urgenciaPastilha * atracaoVisivelPastilhaNorm
```

Ela so fica forte quando:

- o agente consegue comprar pastilha;
- o risco esta alto.

Nesta versao, a urgencia da pastilha foi simplificada para:

```text
urgenciaPastilha = capacidadeCompraPastilha * pressaoRisco
```

Isso significa:

- se o agente nao pode pagar, a heuristica de pastilha zera;
- se o agente pode pagar e o risco sobe, a heuristica cresce;
- a explicacao fica mais direta, sem depender de vulnerabilidade ou imunidade.

## 10. Penalidade De Estagnacao

O metodo `heuristicaEstagnacao()` esta em [src/agente/Poupador.java:146](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:146).

Ele so atua em `PARADO`.

Formula:

```text
estagnacao = -0.20 * estagnacaoNorm
```

Onde:

- `estagnacaoNorm = permanencias / (permanencias + 1)`

Ou seja, a punicao cresce com a repeticao, mas satura abaixo de `1.0`.

### Exemplo concreto

Se o agente fica 5 ticks seguidos parado na mesma celula:

```text
estagnacaoNorm = 5 / 6 = 0.833
estagnacao = -0.20 * 0.833 = -0.167
```

Isso ajuda a quebrar loops irracionais de `PARADO`, mas de forma mais suave e controlada.

## 11. Como O PARADO Funciona Hoje

Hoje `PARADO` e uma acao igual as outras.

Ele:

- passa por admissibilidade;
- recebe heuristicas;
- entra na roleta com score real.

Ele nao usa mais fallback artificial.

Isso foi uma mudanca importante: antes `PARADO` era injetado por fora; agora ele compete de verdade e pode inclusive receber `-INF` se ficar parado for uma acao insegura.

## 12. Conversao De Score Em Peso

O metodo `converterScoresEmPesos()` esta em [src/agente/Poupador.java:176](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:176).

Hoje o codigo nao usa `softmax`.

Ele faz uma roleta linear por deslocamento:

```text
pesoBruto(a) = score(a) - menorScoreValido + EPSILON
```

Depois normaliza:

```text
pesoFinal(a) = pesoBruto(a) / somaDosPesosBrutos
```

### Por que subtrair `menorScoreValido`

Para garantir que todos os pesos brutos das acoes validas fiquem nao negativos e preservem a ordem de preferencia.

Na pratica:

- a pior acao valida fica com peso quase zero;
- a melhor acao valida fica com o maior peso bruto;
- acoes com `-INF` recebem peso `0` e nao entram na roleta.

Se os scores validos forem:

- `-0.4`
- `0.1`
- `0.5`

entao:

```text
menorScoreValido = -0.4
```

e os pesos brutos ficam:

```text
(-0.4 - -0.4) + EPSILON = EPSILON
( 0.1 - -0.4) + EPSILON = 0.5 + EPSILON
( 0.5 - -0.4) + EPSILON = 0.9 + EPSILON
```

Depois disso, o metodo divide cada peso bruto pela soma total para produzir a distribuicao final da roleta.

## 13. Sorteio Final

O metodo `sortear()` esta em [src/agente/Poupador.java:223](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:223).

Ele faz roleta:

- soma os pesos em ordem;
- sorteia um numero aleatorio;
- retorna a acao correspondente ao intervalo.

Entao o agente continua probabilistico, mas dentro do conjunto de acoes que o modelo considerou admissiveis.

## 14. Como O Log Deve Ser Lido

O log gerado em `logDecisao()` em [src/agente/Poupador.java:238](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:238) mostra:

- `score`: score final da acao;
- `peso`: probabilidade final na roleta;
- `valido`: se a celula e transitavel;
- `captEv`: se a seguranca dura bloqueou por captura evitavel;
- `seg`, `ban`, `moe`, `pas`: parcelas do score;
- `rVis`, `rOlf`: riscos crus;
- `vis`: numero de visitas do destino;
- `estag`: permanencias consecutivas, so para `PARADO`.

### Exemplo concreto

Se aparece:

```text
-> PARADO score=-0.167 peso=0.210 estag=5
```

isso quer dizer:

- ficar parado ainda e permitido;
- mas o score dele esta sendo empurrado para baixo;
- parte disso vem da estagnacao acumulada.

## 15. Metodos E Linhas Principais

Esta secao resume os pontos principais do arquivo.

### `acao()` - [linha 47](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:47)

Entrada principal do agente.

Responsabilidades:

- preparar estado;
- montar contexto;
- calcular scores;
- converter para pesos;
- escolher acao.

### `atualizarPermanencia()` - [linha 70](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:70)

Conta quantos ticks seguidos o agente ficou na mesma celula.

Serve para alimentar a penalidade de estagnacao.

### `calcularScores()` - [linha 80](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:80)

E o coracao da decisao.

Sem ele, o agente nao tem preferencias.

### `avaliarAdmissibilidade()` - [linha 105](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:105)

Implementa o filtro duro.

Serve para impedir:

- parede;
- fora da visao;
- outro poupador;
- ladrado em cima;
- captura evitavel.

### `heuristicaSeguranca()` - [linha 117](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:117)

Principal freio de risco.

Agora trabalha com risco normalizado em `0..1`.

### `heuristicaBanco()` - [linha 110](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:110)

Controla retorno financeiro.

Nesta versao, foi simplificada para operar com:

- urgencia normalizada;
- objetivo de banco normalizado.

### `heuristicaMoeda()` - [linha 117](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:117)

Controla coleta de moedas visiveis.

Agora mede apenas atracao visivel normalizada pela melhor opcao do tick.

### `heuristicaPastilha()` - [linha 121](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:121)

Controla compra/uso indireto de pastilha sob pressao.

Usa urgencia baseada em capacidade de compra e pressao de risco, alem de atracao visivel normalizada.

### `heuristicaEstagnacao()` - [linha 126](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:126)

Evita loop irracional de `PARADO`.

Agora a punicao cresce de forma saturada, e nao linear infinita.

### `converterScoresEmPesos()` - [linha 154](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:154)

Transforma utilidade em probabilidade.

### `sortear()` - [linha 189](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:189)

Implementa a roleta.

### `logDecisao()` - [linha 203](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:203)

E a principal ferramenta de depuracao do agente.

### `isDestinoValido(int)` - [linha 289](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:289)

Define o que e celula proibida no nivel estatico.

### `Contexto.isDestinoValido(Acao)` - [linha 280](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:280)

Define o que e destino valido no nivel dinamico.

Importante:

- `PARADO` e valido aqui;
- banco como celula-alvo especial e valido aqui;
- pastilha sem dinheiro e invalida.

### `getUrgenciaBanco()` - [linha 326](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:326)

Traduz carga financeira em urgencia de retorno ao banco, agora em escala `0..1`.

### `getObjetivoBanco()` - [linha 350](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:350)

Resume a qualidade da acao em relacao ao banco:

- deposito real;
- ou combinacao de progresso e proximidade.

### `isDestinoBanco()` e `getBonusDeposito()` - [linhas 351 e 355](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:351)

Ponto critico para detectar deposito real olhando a celula banco do motor, e nao apenas distancia geometrica.

### `getAtracaoVisivelNormalizada()` - [linha 380](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:380)

Transforma a atracao por moeda ou pastilha em valor relativo no intervalo `0..1`.

### `calcularRiscoVisual()` - [linha 381](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:381)

Mede risco vindo de ladroes visiveis.

Nesta versao, ele depende apenas da distancia relativa aos ladroes visiveis, sem multiplicar por imunidade.

### `getRiscoVisualNormalizado()` e `getRiscoOlfativoNormalizado()`

Convertem risco bruto para escala `0..1` usando:

```text
riscoNorm = risco / (1 + risco)
```

### `isCapturaImediataEvitable()` - [linha 397](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:397)

Implementa a seguranca dura.

Ela bloqueia acoes que:

- terminam adjacentes a um ladrão visivel;
- desde que exista outra acao admissivel que nao termine adjacente.

Ou seja:

- ameaca de 1 passo pode virar `-INF`;
- ameaca de 2 passos nao e mais veto duro;
- risco mais distante fica por conta da `heuristicaSeguranca()`, nao da admissibilidade.

## 16. Leitura Final

Se eu tivesse que resumir essa versao do agente em uma frase, seria esta:

> O poupador escolhe probabilisticamente entre acoes admissiveis, usando heuristicas normalizadas em `0..1` para ponderar risco, retorno ao banco, atracao por moedas, uso de pastilha e custo de ficar estagnado.

Essa e a arquitetura mental do arquivo inteiro.
