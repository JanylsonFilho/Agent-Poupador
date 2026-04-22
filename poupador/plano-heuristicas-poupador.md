# Plano Probabilístico de Heurísticas do Poupador

## Modelo geral

Cada heurística gera, para cada direção `d ∈ {N, S, L, O}`, um valor entre `0` e `1`.

```text
Hk(d) ∈ [0, 1]
```

Esse valor representa o quanto a heurística `k` favorece a direção `d`.

A ideia central é que a heurística não deve ser desligada por regra. Quando ela não tiver preferência real entre as direções, ela deve produzir valores iguais para todos os movimentos válidos. Nesse caso, sua roleta local fica uniforme e sua influência desaparece naturalmente na composição final.

## Notação básica

- `Val(d)`: vale `1` se a direção é válida e `0` caso contrário.
- `Rvis(d)`: risco visual na direção `d`, normalizado em `[0,1]`.
- `Rolf(d)`: risco olfativo na direção `d`, normalizado em `[0,1]`.
- `Vis(d)`: nível de visitação da direção `d`, normalizado em `[0,1]`.
- `Ban(d)`: grau de aproximação ao banco pela direção `d`, normalizado em `[0,1]`.
- `Moe(d)`: grau de atratividade da moeda na direção `d`, normalizado em `[0,1]`.
- `Pas(d)`: grau de atratividade da pastilha na direção `d`, normalizado em `[0,1]`.
- `C`: quantidade de moedas carregadas, normalizada em `[0,1]`.
- `I`: imunidade atual, normalizada em `[0,1]`.

## Roleta local de cada heurística

Para cada heurística:

```text
Pk(d) = Hk(d) / Σ Hk(j)
```

onde `j` percorre as quatro direções.

## Contraste da heurística

Para que uma heurística uniforme realmente não interfira na decisão, define-se seu contraste por:

```text
Kk = max Pk(d) - min Pk(d)
```

Interpretação:

- se a heurística distribui as direções igualmente, então `Kk = 0`;
- se a heurística diferencia fortemente as direções, então `Kk` cresce.

Assim, a heurística não precisa ser zerada por condição externa. Quando ela não distingue uma direção da outra, seu próprio contraste se torna nulo.

## Roleta-base neutra

Para garantir uma saída válida quando todas as heurísticas estiverem uniformes ao mesmo tempo, define-se:

```text
Pbase(d) = Val(d) / Σ Val(j)
```

Essa roleta distribui a probabilidade igualmente entre todos os movimentos válidos.

## Fórmulas-base das heurísticas

### 1. Heurística de segurança

Essa heurística mede o quanto cada direção preserva a segurança do agente.

Fórmula-base:

```text
Hseg(d) = Val(d) * (1 - Rvis(d))
```

Leitura:

- se não houver ladrão visível, então `Rvis(d) = 0` nas direções válidas;
- nesse caso, `Hseg(d)` fica igual para todas elas;
- a roleta local da segurança se torna uniforme;
- logo, seu contraste fica `0` e ela não interfere no resultado final.

### 2. Heurística de olfato

Essa heurística mede o risco inferido pelo cheiro do ladrão.

Fórmula-base:

```text
Holf(d) = Val(d) * (1 - Rolf(d))
```

Leitura:

- se não houver cheiro relevante, as direções válidas recebem o mesmo valor;
- direções com cheiro mais forte recebem menor preferência;
- se o cheiro for uniforme ou inexistente, o contraste tende a `0`.

### 3. Heurística de visitação

Essa heurística favorece direções menos exploradas.

Fórmula-base:

```text
Hvis(d) = Val(d) * (1 - Vis(d))
```

Leitura:

- direções menos visitadas recebem valores maiores;
- direções mais repetidas recebem valores menores;
- se todas tiverem o mesmo histórico de visitação, a roleta fica uniforme e o contraste zera.

### 4. Heurística de aproximação ao banco

Essa heurística cresce com a quantidade de moedas carregadas.

Fórmula-base:

```text
Hban(d) = Val(d) * [ (1 - C) + C * Ban(d) ]
```

Leitura:

- se `C = 0`, todas as direções válidas recebem o mesmo valor;
- à medida que `C` cresce, passam a ganhar vantagem as direções que aproximam do banco;
- assim, a influência do banco surge de forma contínua, e não por limiar fixo.

### 5. Heurística de aproximação à moeda

Essa heurística mede o valor de coletar moeda.

Primeiro define-se a relevância local da moeda:

```text
Qmoe = max Moe(d)
```

Fórmula-base:

```text
Hmoe(d) = Val(d) * [ (1 - Qmoe) + Qmoe * Moe(d) ]
```

Leitura:

- se não houver moeda relevante, `Qmoe = 0` e a heurística fica uniforme;
- se houver oportunidade real de coleta, as direções associadas à moeda passam a receber maior preferência;
- a influência cresce de forma natural com a presença da oportunidade.

### 6. Heurística da pastilha

Essa heurística mede o valor estratégico de buscar a pastilha.

Primeiro define-se a vulnerabilidade do agente:

```text
V = 0,5 * (1 - I) + 0,3 * max Rvis(d) + 0,2 * C
```

Depois define-se a relevância local da pastilha:

```text
Qpas = V * max Pas(d)
```

Fórmula-base:

```text
Hpas(d) = Val(d) * [ (1 - Qpas) + Qpas * Pas(d) ]
```

Leitura:

- se a pastilha não for relevante, a heurística fica uniforme;
- se o agente estiver vulnerável e a pastilha for útil, as direções que a aproximam ganham maior preferência;
- o efeito cresce de forma contínua com a vulnerabilidade.

## Pesos das heurísticas

Os pesos mantêm a hierarquia entre critérios.

Pesos-base:

- `wseg = 0,35`
- `wolf = 0,25`
- `wvis = 0,15`
- `wban = 0,15`
- `wmoe = 0,07`
- `wpas = 0,03`

## Pesos dinâmicos

Os pesos-base são ajustados por fatores contínuos.

Definições:

```text
Gseg = 1 + 0,5 * (max Rvis(d) + max Rolf(d))
Golf = 1 + max Rolf(d)
Gvis = 1 + (1 - max(max Rvis(d), max Rolf(d)))
Gban = 1 + C
Gmoe = 1 + (1 - max(max Rvis(d), max Rolf(d))) * (1 - C)
Gpas = 1 + V
```

Pesos ajustados:

```text
w'seg = wseg * Kseg * Gseg
w'olf = wolf * Kolf * Golf
w'vis = wvis * Kvis * Gvis
w'ban = wban * Kban * Gban
w'moe = wmoe * Kmoe * Gmoe
w'pas = wpas * Kpas * Gpas
```

Normalização dos pesos:

```text
wk(din) = w'k / Σ w'j
```

Interpretação:

- se a heurística gerar roleta uniforme, então `Kk = 0` e seu peso efetivo desaparece naturalmente;
- se a heurística diferenciar as direções, seu peso cresce conforme sua relevância e sua hierarquia base.

## Roleta final

A roleta final é calculada por:

```text
Pfinal(d) = normalize( ε * Pbase(d)
                     + wseg(din) * Pseg(d)
                     + wolf(din) * Polf(d)
                     + wvis(din) * Pvis(d)
                     + wban(din) * Pban(d)
                     + wmoe(din) * Pmoe(d)
                     + wpas(din) * Ppas(d) )
```

onde `ε` é uma constante pequena, usada apenas para garantir uma saída válida quando todas as heurísticas estiverem uniformes ao mesmo tempo.

## Interpretação final

O agente não escolhe por regra fixa. Ele:

1. calcula as roletas locais de cada heurística;
2. mede o contraste de cada uma;
3. ajusta os pesos dinamicamente;
4. combina tudo em uma roleta final;
5. escolhe o movimento a partir dessa distribuição.

Assim, quando uma heurística não distingue nenhuma direção, ela continua formalmente presente, mas sua influência desaparece de forma natural, sem precisar ser desligada por condição externa.
