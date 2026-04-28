package agente;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import algoritmo.ProgramaPoupador;
import controle.Constantes;

public class Poupador extends ProgramaPoupador {
    private static final double ADMISSIBILIDADE_NEGADA = Double.NEGATIVE_INFINITY;
    private static final double EPSILON = 1.0e-6;

    // Distribuicao global de importancia das heuristicas; as magnitudes somam 1.0.
    private static final double PESO_SEGURANCA_VISUAL = 0.30;
    private static final double PESO_SEGURANCA_OLFATIVA = 0.15;
    private static final double PESO_BANCO = 0.22;
    private static final double PESO_MOEDA = 0.18;
    private static final double PESO_PASTILHA = 0.08;
    private static final double PESO_ESTAGNACAO_PARADO = 0.07;
    private static final int[][] OFFSETS_VISAO = new int[][] {
        { -2, -2 }, { -1, -2 }, { 0, -2 }, { 1, -2 }, { 2, -2 },
        { -2, -1 }, { -1, -1 }, { 0, -1 }, { 1, -1 }, { 2, -1 },
        { -2, 0 }, { -1, 0 }, { 1, 0 }, { 2, 0 },
        { -2, 1 }, { -1, 1 }, { 0, 1 }, { 1, 1 }, { 2, 1 },
        { -2, 2 }, { -1, 2 }, { 0, 2 }, { 1, 2 }, { 2, 2 }
    };

    private static final int[][] OFFSETS_OLFATO = new int[][] {
        { -1, -1 }, { 0, -1 }, { 1, -1 },
        { -1, 0 }, { 1, 0 },
        { -1, 1 }, { 0, 1 }, { 1, 1 }
    };

    private static final Acao[] ACOES = new Acao[] {
        new Acao(0, 0, 0, -1),
        new Acao(1, 0, -1, 7),
        new Acao(2, 0, 1, 16),
        new Acao(3, 1, 0, 12),
        new Acao(4, -1, 0, 11)
    };

    private final Map<Point, Integer> lugaresVisitados = new HashMap<Point, Integer>();
    private Point ultimaPosicaoObservada;
    private int permanenciasConsecutivas;

    @Override
    public int acao() {
        Point posicaoAtual = sensor.getPosicao();
        // Mantem memoria curta de permanencia para penalizar estagnacao.
        atualizarPermanencia(posicaoAtual);
        // Registra historico de visitas para leitura de comportamento no log.
        registrarVisita(posicaoAtual);

        // Reune os sensores e o estado interno em uma unica visao do tick atual.
        Contexto contexto = new Contexto(
            sensor.getVisaoIdentificacao(),
            sensor.getAmbienteOlfatoLadrao(),
            posicaoAtual,
            sensor.getNumeroDeMoedas(),
            sensor.getNumeroJogadasImunes(),
            lugaresVisitados,
            permanenciasConsecutivas
        );

        // O pipeline do agente e: admissibilidade -> score -> peso -> sorteio.
        double[] scores = calcularScores(contexto);
        double[] pesos = converterScoresEmPesos(scores);
        int acaoEscolhida = sortear(pesos);
        logDecisao(contexto, scores, pesos, acaoEscolhida);
        return acaoEscolhida;
    }

    private void atualizarPermanencia(Point posicaoAtual) {
        // Conta quantos ticks consecutivos o agente observou a mesma posicao.
        if (ultimaPosicaoObservada != null && ultimaPosicaoObservada.equals(posicaoAtual)) {
            permanenciasConsecutivas++;
        } else {
            permanenciasConsecutivas = 0;
        }

        ultimaPosicaoObservada = new Point(posicaoAtual);
    }

    private double[] calcularScores(Contexto contexto) {
        double[] scores = new double[ACOES.length];

        for (int i = 0; i < ACOES.length; i++) {
            Acao acao = ACOES[i];
            // Acoes proibidas ou suicidas saem do jogo com -INF.
            double score = avaliarAdmissibilidade(contexto, acao);

            if (Double.isInfinite(score) && score < 0.0) {
                scores[i] = score;
                continue;
            }

            // As heuristicas restantes competem de forma aditiva no score final.
            score += heuristicaSeguranca(contexto, acao);
            score += heuristicaBanco(contexto, acao);
            score += heuristicaMoeda(contexto, acao);
            score += heuristicaPastilha(contexto, acao);
            score += heuristicaEstagnacao(contexto, acao);

            scores[i] = score;
        }

        return scores;
    }

    private double avaliarAdmissibilidade(Contexto contexto, Acao acao) {
        // Bloqueia parede, fora do mapa, outro poupador, ladrado em cima e afins.
        if (!contexto.isDestinoValido(acao)) {
            return ADMISSIBILIDADE_NEGADA;
        }

        // Bloqueia movimentos que colocam o agente em captura evitavel.
        if (contexto.isCapturaImediataEvitable(acao)) {
            return ADMISSIBILIDADE_NEGADA;
        }

        return 0.0;
    }

    private double heuristicaSeguranca(Contexto contexto, Acao acao) {
        double riscoVisual = contexto.getRiscoVisualNormalizado(acao);
        double riscoOlfativo = contexto.getRiscoOlfativoNormalizado(acao);
        // Definicao heuristica:
        // Hseg(a) = -(0.55 * Rvis(a)) - (0.25 * Rolf(a))
        // onde Rvis e Rolf sao riscos ja normalizados em [0, 1].
        // Assim, quanto maior o risco associado a acao, menor a contribuicao
        // heuristica de seguranca no score final.
        return -(PESO_SEGURANCA_VISUAL * riscoVisual) - (PESO_SEGURANCA_OLFATIVA * riscoOlfativo);
    }

    private double heuristicaBanco(Contexto contexto, Acao acao) {
        double urgencia = contexto.getUrgenciaBanco();
        double objetivoBanco = contexto.getObjetivoBanco(acao);
        // Formula principal do banco:
        // Hbanco(a) = PESO_BANCO * urgenciaBanco * objetivoBanco(a).
        // A heuristica cresce quando o agente carrega mais moedas e quando a acao
        // deposita ou aproxima o agente do banco.
        return PESO_BANCO * urgencia * objetivoBanco;
    }

    private double heuristicaMoeda(Contexto contexto, Acao acao) {
        // Hmoeda(a) = PESO_MOEDA * atracaoMoedaNormalizada(a).
        // Esta heuristica mede apenas o alinhamento geometrico da acao com moedas
        // visiveis; a competicao com risco e banco acontece no score global.
        // Em outras palavras, esta heuristica responde apenas:
        // "o quanto esta acao aponta para moedas visiveis neste tick?"
        return PESO_MOEDA * contexto.getAtracaoVisivelNormalizada(acao, Constantes.numeroMoeda);
    }

    private double heuristicaPastilha(Contexto contexto, Acao acao) {
        // Hpast(a) = PESO_PASTILHA * urgenciaPastilha * atracaoPastilhaNormalizada(a).
        // Esta heuristica responde a duas perguntas:
        // - faz sentido buscar pastilha agora?
        // - esta acao aponta bem para uma pastilha visivel?
        return PESO_PASTILHA * contexto.getUrgenciaPastilha()
            * contexto.getAtracaoVisivelNormalizada(acao, Constantes.numeroPastinhaPoder);
    }

    private double heuristicaEstagnacao(Contexto contexto, Acao acao) {
        // Hestag(PARADO) = -PESO_ESTAGNACAO_PARADO * estagnacaoNorm.
        // Esta heuristica so pune a acao PARADO; movimentos nao recebem essa penalizacao.
        // Quanto mais ticks seguidos o agente insiste em ficar na mesma posicao,
        // mais negativa fica a contribuicao desta heuristica no score.
        if (!acao.isParado()) {
            return 0.0;
        }

        return -PESO_ESTAGNACAO_PARADO * contexto.getEstagnacaoNormalizada();
    }

    private double[] converterScoresEmPesos(double[] scores) {
        double[] pesos = new double[scores.length];
        // Procura o pior score valido para deslocar toda a escala para cima.
        double menorScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < scores.length; i++) {
            if (Double.isInfinite(scores[i]) && scores[i] < 0.0) {
                // Acoes com -INF nao entram na roleta.
                continue;
            }

            menorScore = Math.min(menorScore, scores[i]);
        }

        if (Double.isInfinite(menorScore)) {
            // Se tudo falhou, a roleta cai no fallback de seguranca.
            pesos[0] = 1.0;
            return pesos;
        }

        double soma = 0.0;
        for (int i = 0; i < scores.length; i++) {
            if (Double.isInfinite(scores[i]) && scores[i] < 0.0) {
                // Acao inadmissivel recebe peso zero e nunca sera sorteada.
                pesos[i] = 0.0;
                continue;
            }

            // Roleta linear por deslocamento:
            // pesoBruto(a) = score(a) - menorScoreValido + EPSILON.
            // Isso garante pesos nao negativos, mesmo quando os scores sao negativos,
            // e preserva a ordem de preferencia entre as acoes validas.
            // Assim, a pior acao valida fica muito perto de zero e a melhor recebe
            // o maior peso bruto antes da normalizacao.
            pesos[i] = (scores[i] - menorScore) + EPSILON;
            soma += pesos[i];
        }

        for (int i = 0; i < pesos.length; i++) {
            // Normaliza para que a soma final dos pesos seja 1.0, formando uma
            // distribuicao de probabilidades para a roleta do metodo sortear().
            pesos[i] /= soma;
        }

        return pesos;
    }

    private int sortear(double[] pesos) {
        double sorteio = Math.random();
        double acumulado = 0.0;

        // Roleta proporcional: score melhor aumenta chance, mas nao garante determinismo.
        for (int i = 0; i < pesos.length; i++) {
            acumulado += pesos[i];
            if (sorteio <= acumulado) {
                return ACOES[i].codigo;
            }
        }

        return ACOES[0].codigo;
    }

    private void logDecisao(Contexto contexto, double[] scores, double[] pesos, int acaoEscolhida) {
        StringBuilder log = new StringBuilder();
        log.append("[POUPADOR ").append(getIdentificadorAgente()).append("] pos=").append(formatarPosicao(contexto.getPosicaoAtual()));
        log.append(" moedas=").append(sensor.getNumeroDeMoedas());
        log.append(" banco=").append(sensor.getNumeroDeMoedasBanco());
        log.append(" imune=").append(sensor.getNumeroJogadasImunes());
        log.append(" riscoVis=").append(formatarDouble(contexto.calcularRiscoVisual(ACOES[0])));
        log.append(" riscoOlf=").append(formatarDouble(contexto.calcularRiscoOlfativo(ACOES[0])));
        log.append(" escolha=").append(nomeAcao(acaoEscolhida));
        System.out.println(log.toString());

        for (int i = 0; i < ACOES.length; i++) {
            Acao acao = ACOES[i];
            StringBuilder linha = new StringBuilder("  -> ");
            linha.append(nomeAcao(acao.codigo));
            linha.append(" score=").append(formatarScore(scores[i]));
            linha.append(" peso=").append(formatarDouble(pesos[i]));
            linha.append(" destino=").append(formatarPosicao(contexto.getPosicaoDestino(acao)));
            if (!acao.isParado()) {
                double seg = heuristicaSeguranca(contexto, acao);
                double ban = heuristicaBanco(contexto, acao);
                double moe = heuristicaMoeda(contexto, acao);
                double pas = heuristicaPastilha(contexto, acao);
                linha.append(" valido=").append(contexto.isDestinoValido(acao));
                linha.append(" captEv=").append(contexto.isCapturaImediataEvitable(acao));
                linha.append(" seg=").append(formatarDouble(seg));
                linha.append(" ban=").append(formatarDouble(ban));
                linha.append(" moe=").append(formatarDouble(moe));
                linha.append(" pas=").append(formatarDouble(pas));
                linha.append(" vis=").append(contexto.getVisitas(acao));
                linha.append(" banco=").append(formatarDouble(contexto.getAtracaoBanco(acao)));
                linha.append(" moeda=").append(formatarDouble(contexto.getAtracaoVisivel(acao, Constantes.numeroMoeda)));
                linha.append(" past=").append(formatarDouble(contexto.getAtracaoVisivel(acao, Constantes.numeroPastinhaPoder)));
                linha.append(" rVis=").append(formatarDouble(contexto.calcularRiscoVisual(acao)));
                linha.append(" rOlf=").append(formatarDouble(contexto.calcularRiscoOlfativo(acao)));
            } else {
                linha.append(" estag=").append(contexto.getPermanenciasConsecutivas());
            }
            System.out.println(linha.toString());
        }
    }

    private String getIdentificadorAgente() {
        // Usa o hash de identidade apenas para diferenciar instancias no log.
        return Integer.toHexString(System.identityHashCode(this));
    }

    private String nomeAcao(int codigo) {
        // Traduz o codigo numerico da acao para um nome legivel na depuracao.
        switch (codigo) {
            case 0:
                return "PARADO";
            case 1:
                return "CIMA";
            case 2:
                return "BAIXO";
            case 3:
                return "DIREITA";
            case 4:
                return "ESQUERDA";
            default:
                return "ACAO_" + codigo;
        }
    }

    private String formatarPosicao(Point posicao) {
        // Padroniza a impressao de coordenadas no formato (x,y).
        return "(" + posicao.x + "," + posicao.y + ")";
    }

    private String formatarScore(double score) {
        // Preserva a leitura de acoes bloqueadas como -INF no log final.
        if (Double.isInfinite(score) && score < 0.0) {
            return "-INF";
        }
        return formatarDouble(score);
    }

    private String formatarDouble(double valor) {
        // Limita a exibicao numerica para 3 casas, facilitando comparacao visual.
        return String.format(java.util.Locale.US, "%.3f", valor);
    }

    private void registrarVisita(Point posicao) {
        Point chave = new Point(posicao);
        Integer visitas = lugaresVisitados.get(chave);
        // O mapa de visitas hoje alimenta observabilidade; pode virar heuristica no futuro.
        lugaresVisitados.put(chave, Integer.valueOf(visitas == null ? 1 : visitas.intValue() + 1));
    }

    private static boolean isDestinoValido(int celula) {
        // Define, no nivel estatico, quais codigos de celula sao atravessaveis.
        if (celula == Constantes.foraAmbiene || celula == Constantes.semVisao || celula == Constantes.numeroParede) {
            return false;
        }

        if (celula == Constantes.numeroPoupador01 || celula == Constantes.numeroPoupador02) {
            return false;
        }

        return !isLadrao(celula);
    }

    private static boolean isLadrao(int celula) {
        // Reconhece qualquer um dos codigos visuais associados a agentes ladrões.
        return celula >= Constantes.numeroLadrao01 && celula <= Constantes.numeroLadrao04;
    }

    private static double intensidadeOlfato(int marca) {
        // Converte a marca olfativa discreta do sensor em intensidade continua de risco.
        if (marca <= 0) {
            return 0.0;
        }

        return (6.0 - marca) / 5.0;
    }

    private static final class Acao {
        private final int codigo;
        private final int dx;
        private final int dy;
        private final int indiceVisao;

        private Acao(int codigo, int dx, int dy, int indiceVisao) {
            // Cada acao guarda codigo do atuador, deslocamento cartesiano e indice
            // correspondente na matriz de visao do simulador.
            this.codigo = codigo;
            this.dx = dx;
            this.dy = dy;
            this.indiceVisao = indiceVisao;
        }

        private boolean isParado() {
            // Convenio local: o codigo 0 representa a acao de permanecer na celula atual.
            return codigo == 0;
        }
    }

    private static final class Contexto {
        private final int[] visao;
        private final int[] olfatoLadrao;
        private final Point posicao;
        private final int numeroMoedas;
        private final int numeroJogadasImunes;
        private final Map<Point, Integer> lugaresVisitados;
        private final int permanenciasConsecutivas;

        private Contexto(
            int[] visao,
            int[] olfatoLadrao,
            Point posicao,
            int numeroMoedas,
            int numeroJogadasImunes,
            Map<Point, Integer> lugaresVisitados,
            int permanenciasConsecutivas
        ) {
            // Copia o estado relevante do tick para centralizar os calculos heurísticos.
            this.visao = visao;
            this.olfatoLadrao = olfatoLadrao;
            this.posicao = new Point(posicao);
            this.numeroMoedas = numeroMoedas;
            this.numeroJogadasImunes = numeroJogadasImunes;
            this.lugaresVisitados = lugaresVisitados;
            this.permanenciasConsecutivas = permanenciasConsecutivas;
        }

        private boolean isDestinoValido(Acao acao) {
            // Junta regras dinamicas do turno com a validacao estatica da celula.
            if (acao.isParado()) {
                return true;
            }

            // O banco e uma celula especial do motor: o agente deposita ao tentar entrar nela.
            if (isDestinoBanco(acao)) {
                return true;
            }

            // Evita escolher pastilha quando o agente nao consegue pagar.
            if (visao[acao.indiceVisao] == Constantes.numeroPastinhaPoder && numeroMoedas < Constantes.custoPastinha) {
                return false;
            }
            return Poupador.isDestinoValido(visao[acao.indiceVisao]);
        }

        private Point getPosicaoAtual() {
            // Retorna copia defensiva da posicao para evitar aliasing no log.
            return new Point(posicao);
        }

        private int getPermanenciasConsecutivas() {
            // Expõe a memoria curta de permanencia para a heuristica de estagnacao.
            return permanenciasConsecutivas;
        }

        private int getVisitas(Acao acao) {
            // Consulta quantas vezes o destino desta acao ja apareceu no historico.
            Integer visitas = lugaresVisitados.get(getPosicaoDestino(acao));
            return visitas == null ? 0 : visitas.intValue();
        }

        private Point getPosicaoDestino(Acao acao) {
            // Projeta a coordenada final da acao a partir da posicao atual e do deslocamento.
            return new Point(posicao.x + acao.dx, posicao.y + acao.dy);
        }

        private double getCargaFinanceira() {
            // C = min(1, moedas / 10): converte a quantidade de moedas em carga
            // financeira normalizada para a heuristica do banco.
            return Math.min(1.0, numeroMoedas / 10.0);
        }

        private double getImunidade() {
            // Normaliza os ticks de imunidade restantes no intervalo [0, 1].
            return Math.min(1.0, numeroJogadasImunes / (double) Constantes.numeroTICsImunes);
        }

        private double getCapacidadeCompraPastilha() {
            // CapCompra = 1 se o agente pode pagar a pastilha; caso contrario, 0.
            return numeroMoedas >= Constantes.custoPastinha ? 1.0 : 0.0;
        }

        private double getUrgenciaPastilha() {
            // Upast = capacidadeCompra * pressaoRisco.
            // A pastilha so ganha relevancia quando o agente consegue pagar por ela
            // e quando o estado atual esta sob pressao de risco.
            // Se uma dessas duas componentes for 0, a urgencia da pastilha zera.
            return getCapacidadeCompraPastilha() * getPressaoRisco();
        }

        private double getPressaoRisco() {
            // PressaoRisco = max(riscoVisualNorm, riscoOlfativoNorm).
            // Usa o pior risco imediato do estado como referencia do tick, ja em [0, 1].
            return Math.max(getRiscoVisualNormalizado(ACOES[0]), getRiscoOlfativoNormalizado(ACOES[0]));
        }

        private double getUrgenciaBanco() {
            double carga = getCargaFinanceira();
            // Ubanco = (C * (1 + C)) / 2.
            // Curva crescente em [0, 1]: com poucas moedas o banco pesa pouco;
            // com muitas moedas a urgencia de depositar aumenta mais rapido.
            return (carga * (1.0 + carga)) / 2.0;
        }

        private double getAtracaoBanco(Acao acao) {
            if (isDestinoBanco(acao)) {
                return 1.0;
            }

            // Atracao geometrica global do banco: quanto menor a distancia Manhattan
            // do destino ao banco, maior o valor retornado.
            Point destino = getPosicaoDestino(acao);
            int distancia = Math.abs(destino.x - Constantes.posicaoBanco.x) + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return 1.0 / (distancia + 1.0);
        }

        private double getProximidadeLocalBanco(Acao acao) {
            if (isDestinoBanco(acao)) {
                return 1.0;
            }

            // Proximidade local do banco: so da bonus quando o destino cai na
            // vizinhanca curta do banco, para diferenciar "estar perto" de "estar longe".
            Point destino = getPosicaoDestino(acao);
            int distancia = Math.abs(destino.x - Constantes.posicaoBanco.x) + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return distancia <= 2 ? (1.0 / (distancia + 1.0)) : 0.0;
        }

        private double getObjetivoBanco(Acao acao) {
            if (getBonusDeposito(acao) > 0.0) {
                // Depositar e o melhor caso possivel da heuristica bancaria.
                return 1.0;
            }

            double progresso = Math.max(0.0, getProgressoBanco(acao));
            double proximidade = getProximidadeLocalBanco(acao);
            // Obanco(a) = (progresso + proximidade) / 2.
            // Combina "encurtar o caminho" com "terminar perto do banco" na mesma escala.
            return (progresso + proximidade) / 2.0;
        }

        private double getProgressoBanco(Acao acao) {
            // Progresso bruto: distanciaAtual - distanciaDestino.
            // Valor positivo significa que a acao aproximou o agente do banco.
            int distanciaAtual = Math.abs(posicao.x - Constantes.posicaoBanco.x)
                + Math.abs(posicao.y - Constantes.posicaoBanco.y);
            Point destino = getPosicaoDestino(acao);
            int distanciaDestino = Math.abs(destino.x - Constantes.posicaoBanco.x)
                + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return distanciaAtual - distanciaDestino;
        }

        private double getBonusDeposito(Acao acao) {
            // BonusDeposito(a) = 1 apenas quando a acao tenta entrar na celula banco
            // com moedas na mao; fora disso, nao ha deposito.
            return isDestinoBanco(acao) && numeroMoedas > 0 ? 1.0 : 0.0;
        }

        private boolean isDestinoBanco(Acao acao) {
            // No motor do jogo, depositar significa mirar a celula banco no campo de visao.
            return !acao.isParado() && visao[acao.indiceVisao] == Constantes.numeroBanco;
        }

        private double getAtracaoVisivel(Acao acao, int tipoAlvo) {
            double atracao = 0.0;

            for (int i = 0; i < visao.length; i++) {
                if (visao[i] != tipoAlvo) {
                    continue;
                }

                int[] offset = OFFSETS_VISAO[i];
                // AtracaoBruta(a) = soma_j [ 1 / (d(a, alvo_j) + 1) ].
                // Cada alvo visivel gera uma parcela individual:
                // - se a distancia for 0, a contribuicao vale 1.0;
                // - quanto maior a distancia Manhattan entre a direcao da acao e o alvo,
                //   menor a contribuicao dessa parcela.
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                atracao += 1.0 / (distancia + 1.0);
            }

            return atracao;
        }

        private double getAtracaoVisivelNormalizada(Acao acao, int tipoAlvo) {
            double maior = getMaiorAtracaoVisivel(tipoAlvo);
            if (maior <= 0.0) {
                return 0.0;
            }

            // Normalizacao relativa no tick:
            // atracaoNorm(a) = atracaoBruta(a) / maiorAtracaoDoTick.
            // Isso coloca a heuristica em escala comparavel com as demais:
            // - a melhor acao para aquele alvo recebe 1.0;
            // - as outras ficam entre 0 e 1;
            // - sem normalizacao, a quantidade de alvos visiveis poderia inflar
            //   os numeros e distorcer a competicao entre heuristicas.
            return getAtracaoVisivel(acao, tipoAlvo) / maior;
        }

        private double getMaiorAtracaoVisivel(int tipoAlvo) {
            double maior = 0.0;

            for (int i = 0; i < ACOES.length; i++) {
                maior = Math.max(maior, getAtracaoVisivel(ACOES[i], tipoAlvo));
            }

            // Serve como denominador da normalizacao: representa a melhor atracao
            // possivel entre as acoes disponiveis no estado atual.
            return maior;
        }


        private double calcularRiscoVisual(Acao acao) {
            double risco = 0.0;

            // rvis(a) = soma_i [ 1 / max(1, d(a, li)) ]
            // li e a posicao relativa do ladrão i na visao.
            // d(a, li) e a distancia Manhattan entre a acao e o ladrão.
            for (int i = 0; i < visao.length; i++) {
                if (!isLadrao(visao[i])) {
                    continue;
                }

                int[] offset = OFFSETS_VISAO[i];
                // d(a, li) = |dx - lx| + |dy - ly|
                // Ladrao perto pesa mais na soma do risco visual.
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                risco += 1.0 / Math.max(1.0, distancia);
            }

            return risco;
        }

        private double getRiscoVisualNormalizado(Acao acao) {
            // Rvis(a) = rvis(a) / (1 + rvis(a))
            // A normalizacao evita que riscos brutos muito altos explodam a escala.
            return normalizarRisco(calcularRiscoVisual(acao));
        }

        private boolean isCapturaImediataEvitable(Acao acao) {
            if (getImunidade() > 0.0) {
                return false;
            }

            int distanciaAcao = menorDistanciaVisual(acao);

            // Veto duro apenas para ameaca realmente imediata:
            // se a acao termina adjacente a um ladrão visivel e existe ao menos
            // uma alternativa admissivel que nao termina adjacente, bloqueamos.
            if (distanciaAcao > 1) {
                return false;
            }

            for (int i = 0; i < ACOES.length; i++) {
                Acao candidata = ACOES[i];
                if (candidata == acao || !isDestinoValido(candidata)) {
                    continue;
                }

                if (menorDistanciaVisual(candidata) > 1) {
                    return true;
                }
            }

            return false;
        }

        private int menorDistanciaVisual(Acao acao) {
            int menor = Integer.MAX_VALUE;

            // Resume a ameaca visual imediata da acao pela menor distancia a qualquer ladrão.
            for (int i = 0; i < visao.length; i++) {
                if (!isLadrao(visao[i])) {
                    continue;
                }

                int[] offset = OFFSETS_VISAO[i];
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                menor = Math.min(menor, distancia);
            }

            return menor;
        }


        private double calcularRiscoOlfativo(Acao acao) {
            double risco = 0.0;

            // rolf(a) = soma_i [ Oi / (d(a, oi) + 1) ]
            // oi e a posicao relativa da marca de olfato i.
            // Oi e a intensidade da marca, calculada a partir do valor lido no sensor.
            for (int i = 0; i < olfatoLadrao.length; i++) {
                double intensidade = intensidadeOlfato(olfatoLadrao[i]);
                if (intensidade == 0.0) {
                    continue;
                }

                int[] offset = OFFSETS_OLFATO[i];
                // d(a, oi) = |dx - ox| + |dy - oy|
                // O cheiro e tratado como risco difuso de curto alcance.
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                risco += intensidade / (distancia + 1.0);
            }

            return risco;
        }

        private double getRiscoOlfativoNormalizado(Acao acao) {
            // Rolf(a) = rolf(a) / (1 + rolf(a))
            // Mantem a contribuicao olfativa na mesma escala da visual.
            return normalizarRisco(calcularRiscoOlfativo(acao));
        }

        private double getEstagnacaoNormalizada() {
            // estagnacaoNorm = permanencias / (permanencias + 1).
            // Cresce com a repeticao, mas satura abaixo de 1.0:
            // - no inicio a punicao sobe rapido;
            // - depois continua aumentando, mas sem explodir numericamente.
            return permanenciasConsecutivas <= 0 ? 0.0 : permanenciasConsecutivas / (permanenciasConsecutivas + 1.0);
        }

        private double normalizarRisco(double risco) {
            // Funcao de normalizacao:
            // N(r) = r / (1 + r), com imagem em [0, 1).
            return risco <= 0.0 ? 0.0 : risco / (1.0 + risco);
        }
    }
}
