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
   - exploracao
   - banco
   - moeda
   - pastilha
   - estagnacao

Formula conceitual:

```text
score(acao) =
    admissibilidade
  + seguranca
  + exploracao
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
- mas ir para la deixa o poupador em uma faixa de captura que poderia ser evitada;
- por isso a acao foi bloqueada.

## 6. Heuristica De Seguranca

O metodo `heuristicaSeguranca()` esta em [src/agente/Poupador.java:117](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:117).

Ele usa:

- risco visual
- risco olfativo

Formula:

```text
seguranca = -(8.0 * riscoVisual) - (4.0 * riscoOlfativo)
```

Quanto maior o risco, mais negativo fica o score.

### Exemplo concreto

Se uma acao tem:

```text
rVis=0.500
rOlf=0.000
```

entao a parcela de seguranca fica:

```text
seg = -(8 * 0.5) - (4 * 0.0) = -4.0
```

que bate com logs como:

```text
seg=-4.000
```

## 7. Heuristica De Exploracao

O metodo `heuristicaExploracao()` esta em [src/agente/Poupador.java:123](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:123).

Ideia:

- a acao recebe uma probabilidade de exploracao;
- o agente transforma isso em score com `log(probabilidade + EPSILON)`.

Como `log` de numero entre `0` e `1` e negativo, exploracao normalmente aparece como custo.
Mas esse custo e menor para acoes que recebem mais massa exploratoria.

O calculo da probabilidade esta em `getProbabilidadeExploracao()` em [src/agente/Poupador.java:495](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:495).

E a massa exploratoria esta em `getMassaExploracao()` em [src/agente/Poupador.java:519](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:519).

Ela favorece:

- entrar no banco;
- pegar pastilha visivel;
- celulas menos visitadas.

## 8. Heuristica Do Banco

O metodo `heuristicaBanco()` esta em [src/agente/Poupador.java:129](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:129).

Ela depende de:

- urgencia financeira;
- bonus de deposito;
- progresso em direcao ao banco;
- proximidade local do banco.

Formula:

```text
banco =
PESO_BANCO * urgencia *
((18 * deposito) + (0.6 * progresso) + (0.4 * proximidade))
```

### Importante

O bonus de deposito foi corrigido em [src/agente/Poupador.java:452](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:452) para so valer quando:

- o destino e o banco;
- o agente ainda nao esta sobre o banco;
- o agente tem moedas.

Isso evita o bug em que `PARADO` sobre o banco ganhava "deposito infinito" em loop.

### Exemplo concreto

Se o agente esta perto do banco, cheio de moedas e pode depositar, a parcela `ban` sobe muito.
Foi isso que antes produzia logs como:

```text
ban=25.256
```

Agora esse comportamento so deve acontecer na entrada real no banco, nao em repeticao parado em cima dele.

## 9. Heuristica De Moeda

O metodo `heuristicaMoeda()` esta em [src/agente/Poupador.java:137](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:137).

Formula:

```text
moeda = PESO_MOEDA * relevanciaMoeda * atracaoVisivelMoeda
```

Ela fica mais forte quando:

- ha moedas visiveis;
- o risco nao esta alto;
- a urgencia do banco nao esta dominando.

`getRelevanciaMoeda()` em [src/agente/Poupador.java:421](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:421) foi corrigido para nao ficar negativa.

## 10. Heuristica De Pastilha

O metodo `heuristicaPastilha()` esta em [src/agente/Poupador.java:141](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:141).

Formula:

```text
pastilha = PESO_PASTILHA * urgenciaPastilha * atracaoVisivelPastilha
```

Ela so fica forte quando:

- o agente consegue comprar pastilha;
- esta vulneravel;
- o risco esta alto.

## 11. Penalidade De Estagnacao

O metodo `heuristicaEstagnacao()` esta em [src/agente/Poupador.java:146](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:146).

Ele so atua em `PARADO`.

Formula:

```text
estagnacao = -0.8 * permanenciasConsecutivas
```

Ou seja:

- primeira permanencia: `0`
- segunda permanencia: `-0.8`
- terceira: `-1.6`
- quarta: `-2.4`

e assim por diante.

### Exemplo concreto

Se o agente fica 5 ticks seguidos parado na mesma celula, a parte de estagnacao sozinha tira:

```text
-0.8 * 5 = -4.0
```

Isso ajuda a quebrar loops irracionais.

## 12. Como O PARADO Funciona Hoje

Hoje `PARADO` e uma acao igual as outras.

Ele:

- passa por admissibilidade;
- recebe heuristicas;
- entra na roleta com score real.

Ele nao usa mais fallback artificial.

Isso foi uma mudanca importante: antes `PARADO` era injetado por fora; agora ele compete de verdade.

## 13. Conversao De Score Em Peso

O metodo `converterScoresEmPesos()` esta em [src/agente/Poupador.java:154](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:154).

Ele faz um softmax simplificado:

```text
peso = exp(score - maiorScore)
```

Depois normaliza a soma para 1.

### Por que subtrair `maiorScore`

Para evitar explosao numerica.

Se os scores forem:

- `2`
- `1`
- `-3`

o codigo usa:

```text
exp(2-2), exp(1-2), exp(-3-2)
```

ou seja:

```text
exp(0), exp(-1), exp(-5)
```

que e numericamente estavel.

## 14. Sorteio Final

O metodo `sortear()` esta em [src/agente/Poupador.java:189](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:189).

Ele faz roleta:

- soma os pesos em ordem;
- sorteia um numero aleatorio;
- retorna a acao correspondente ao intervalo.

Entao o agente continua probabilistico, mas dentro do conjunto de acoes que o modelo considerou admissiveis.

## 15. Como O Log Deve Ser Lido

O log gerado em `logDecisao()` em [src/agente/Poupador.java:203](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:203) mostra:

- `score`: score final da acao;
- `peso`: probabilidade final na roleta;
- `valido`: se a celula e transitavel;
- `captEv`: se a seguranca dura bloqueou por captura evitavel;
- `seg`, `exp`, `ban`, `moe`, `pas`: parcelas do score;
- `rVis`, `rOlf`: riscos crus;
- `vis`: numero de visitas do destino;
- `estag`: permanencias consecutivas, so para `PARADO`.

### Exemplo concreto

Se aparece:

```text
-> PARADO score=-12.600 peso=0.356 estag=8
```

isso quer dizer:

- ficar parado ainda e permitido;
- mas o score dele esta sendo empurrado para baixo;
- parte disso vem da estagnacao acumulada.

## 16. Metodos E Linhas Principais

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

Se ela estiver fraca, o agente morre por ganancia.

### `heuristicaExploracao()` - [linha 123](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:123)

Evita que o agente fique preso explorando sempre as mesmas rotas.

### `heuristicaBanco()` - [linha 129](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:129)

Controla retorno financeiro.

Foi uma das heuristicas mais sensiveis do projeto, porque se ficar forte demais vira ima global do banco.

### `heuristicaMoeda()` - [linha 137](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:137)

Controla coleta de moedas visiveis.

### `heuristicaPastilha()` - [linha 141](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:141)

Controla compra/uso indireto de pastilha sob pressao.

### `heuristicaEstagnacao()` - [linha 146](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:146)

Evita loop irracional de `PARADO`.

### `converterScoresEmPesos()` - [linha 154](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:154)

Transforma utilidade em probabilidade.

### `sortear()` - [linha 189](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:189)

Implementa a roleta.

### `logDecisao()` - [linha 203](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:203)

E a principal ferramenta de depuracao do agente.

### `isDestinoValido(int)` - [linha 289](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:289)

Define o que e celula proibida no nivel estatico.

### `Contexto.isDestinoValido(Acao)` - [linha 358](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:358)

Define o que e destino valido no nivel dinamico.

Importante:

- `PARADO` e valido aqui;
- pastilha sem dinheiro e invalida.

### `getRelevanciaExploracao()` - [linha 414](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:414)

Escala o quanto explorar ainda faz sentido.

### `getRelevanciaMoeda()` - [linha 421](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:421)

Escala o valor de perseguir moeda.

### `getUrgenciaBanco()` - [linha 426](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:426)

Traduz carga financeira em urgencia de retorno ao banco.

### `getBonusDeposito()` - [linha 452](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:452)

Ponto critico para evitar bonus falso quando o agente ja esta parado sobre o banco.

### `getProbabilidadeExploracao()` - [linha 495](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:495)

Distribui a massa exploratoria entre acoes admissiveis.

### `calcularRiscoVisual()` - [linha 533](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:533)

Mede risco vindo de ladroes visiveis.

### `isCapturaImediataEvitable()` - [linha 550](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:550)

Implementa a seguranca dura.

Ela bloqueia acoes que:

- deixam o agente em distancia visual muito ruim;
- ou deixam risco composto muito pior que a melhor alternativa.

### `calcularRiscoComposto()` - [linha 617](/home/janylson/Documentos/GITHUB/Agent-Poupador/poupador/src/agente/Poupador.java:617)

Combina visual e olfativo:

```text
riscoComposto = riscoVisual + 0.5 * riscoOlfativo
```

## 17. Leitura Final

Se eu tivesse que resumir essa versao do agente em uma frase, seria esta:

> O poupador escolhe probabilisticamente entre acoes admissiveis, ponderando risco, exploracao, retorno ao banco, atracao por moedas, uso de pastilha e custo de ficar estagnado.

Essa e a arquitetura mental do arquivo inteiro.
