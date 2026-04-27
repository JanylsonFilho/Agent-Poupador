# Plano de Refatoracao do Poupador

## Objetivo

Refatorar o `Poupador` para que ele:

- continue autonomo;
- continue usando roleta;
- deixe de escolher movimentos irracionais;
- fique simples de explicar e manter.

## Ideia central

O agente deve separar duas etapas:

1. `admissibilidade`: decidir quais movimentos podem participar da decisao;
2. `preferencia`: decidir, por roleta, qual movimento admissivel sera escolhido.

## Regra do menos infinito

Se um movimento nao puder ser escolhido de jeito nenhum naquele estado, ele recebe:

```text
score(d) = -infinito
```

Interpretacao:

- `-infinito` significa movimento proibido;
- movimento proibido recebe peso `0` na roleta;
- logo, sua fatia desaparece completamente.

Isso preserva a teoria probabilistica da roleta, porque a roleta continua existindo, mas apenas entre os movimentos admissiveis.

## Pipeline de decisao

O `Poupador` deve funcionar em 5 passos:

1. listar as 5 acoes candidatas: parado, cima, baixo, direita e esquerda;
2. eliminar os movimentos proibidos com `-infinito`;
3. calcular a pontuacao heuristica dos movimentos restantes;
4. converter as pontuacoes em pesos;
5. rodar a roleta final.

## Acoes consideradas

O framework do jogo aceita explicitamente 5 acoes para o `Poupador`:

- `0`: ficar parado;
- `1`: mover para cima;
- `2`: mover para baixo;
- `3`: mover para a direita;
- `4`: mover para a esquerda.

Logo, `ficar parado` deve ser tratado como uma acao real do agente e precisa entrar na avaliacao de admissibilidade, no calculo de score e na roleta final.

## Formula simples

Para cada direcao `d`:

```text
Score(d) = -infinito, se d for inadmissivel
Score(d) = Hseg(d) + Holf(d) + Hvis(d) + Hban(d) + Hmoe(d) + Hpas(d), caso contrario
```

Depois:

```text
Peso(d) = 0, se Score(d) = -infinito
Peso(d) = normalizar(Score(d)), caso contrario
```

Se for necessario evitar score negativo, usar esta versao:

```text
Peso(d) = 0, se Score(d) = -infinito
Peso(d) = max(epsilon, Score(d) - menorScore + epsilon), caso contrario
```

onde `epsilon` e um numero pequeno positivo.

## Quando usar menos infinito

Usar `-infinito` apenas em casos proibitivos.

Exemplos:

- mover para parede;
- mover para fora do mapa;
- mover para uma celula ocupada por ladrao;
- mover para uma celula com captura imediata, se existir alternativa segura;
- ficar parado apenas quando o proprio framework nao permitisse essa acao no estado, o que nao e o caso aqui.

## Quando nao usar menos infinito

Nao usar `-infinito` em preferencias fracas.

Exemplos:

- ficar parado quando a acao e valida, mas estrategicamente ruim;
- caminho menos interessante para moeda;
- caminho mais visitado;
- caminho que afasta do banco;
- pastilha pouco relevante.

Nesses casos, o movimento continua valido, apenas com score menor.

## Regra de racionalidade

O agente continua autonomo porque:

- ele mesmo observa o estado;
- ele mesmo calcula os movimentos admissiveis;
- ele mesmo escolhe, por roleta, entre os admissiveis.

O agente deixa de ser arbitrario porque:

- movimentos irracionais nao entram no sorteio.

## Estrutura de codigo desejada

O codigo do `Poupador` deve ser reorganizado assim:

### 1. Admissibilidade

Funcao:

```text
avaliarAdmissibilidade(contexto, movimento) -> 0 ou -infinito
```

Saida:

- `0` se o movimento ainda pode competir;
- `-infinito` se o movimento deve ser eliminado.

### 2. Heuristicas escalares

Cada heuristica deve devolver apenas um numero.

Exemplos:

```text
heuristicaSeguranca(contexto, movimento)
heuristicaOlfato(contexto, movimento)
heuristicaVisitacao(contexto, movimento)
heuristicaBanco(contexto, movimento)
heuristicaMoeda(contexto, movimento)
heuristicaPastilha(contexto, movimento)
```

Importante:

- cada heuristica devolve score;
- nenhuma heuristica cria roleta propria.

### 3. Score total

```text
scoreTotal = admissibilidade + somaDasHeuristicas
```

Se a admissibilidade for `-infinito`, o score final continua `-infinito`.

### 4. Roleta unica

Somente no final:

- converter scores validos em pesos;
- normalizar os pesos;
- sortear uma acao.

## Vantagens da refatoracao

- reduz complexidade;
- facilita explicar ao professor;
- separa proibicao de preferencia;
- preserva autonomia;
- preserva roleta;
- melhora racionalidade do agente.

## Ordem de implementacao

### Etapa 1

Remover a logica de varias roletas internas.

### Etapa 2

Criar a funcao unica de admissibilidade com `-infinito`.

### Etapa 3

Fazer cada heuristica retornar apenas score escalar.

### Etapa 4

Somar os scores em uma unica funcao de avaliacao.

### Etapa 5

Aplicar uma unica roleta no final.

## Regra final do projeto

Frase simples para defender o projeto:

> O menos infinito remove da roleta os movimentos inadmissiveis. A autonomia do agente continua existindo na escolha probabilistica entre os movimentos admissiveis.
