package agente;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import algoritmo.ProgramaPoupador;
import controle.Constantes;

public class Poupador extends ProgramaPoupador {
    private static final double ADMISSIBILIDADE_NEGADA = Double.NEGATIVE_INFINITY;
    private static final double EPSILON = 1.0e-6;

    private static final double PESO_SEGURANCA_VISUAL = 0.55;
    private static final double PESO_SEGURANCA_OLFATIVA = 0.25;
    private static final double PESO_BANCO = 0.45;
    private static final double PESO_MOEDA = 0.35;
    private static final double PESO_PASTILHA = 0.20;
    private static final double PESO_ESTAGNACAO_PARADO = 0.20;
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
        // Risco sempre entra como custo normalizado; visual pesa mais que olfato.
        return -(PESO_SEGURANCA_VISUAL * riscoVisual) - (PESO_SEGURANCA_OLFATIVA * riscoOlfativo);
    }

    private double heuristicaBanco(Contexto contexto, Acao acao) {
        double urgencia = contexto.getUrgenciaBanco();
        double objetivoBanco = contexto.getObjetivoBanco(acao);
        // Deposito real continua dominando, mas a formula agora opera so com termos em [0, 1].
        return PESO_BANCO * urgencia * objetivoBanco;
    }

    private double heuristicaMoeda(Contexto contexto, Acao acao) {
        // Moeda visivel so conta de fato quando o risco e a urgencia do banco permitem.
        return PESO_MOEDA * contexto.getRelevanciaMoeda()
            * contexto.getAtracaoVisivelNormalizada(acao, Constantes.numeroMoeda);
    }

    private double heuristicaPastilha(Contexto contexto, Acao acao) {
        // Pastilha ganha relevancia quando o agente pode comprar e esta vulneravel.
        return PESO_PASTILHA * contexto.getUrgenciaPastilha()
            * contexto.getAtracaoVisivelNormalizada(acao, Constantes.numeroPastinhaPoder);
    }

    private double heuristicaEstagnacao(Contexto contexto, Acao acao) {
        // So pune ficar parado; nao pune movimento repetido.
        if (!acao.isParado()) {
            return 0.0;
        }

        return -PESO_ESTAGNACAO_PARADO * contexto.getEstagnacaoNormalizada();
    }

    private double[] converterScoresEmPesos(double[] scores) {
        double[] pesos = new double[scores.length];
        double maiorScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < scores.length; i++) {
            if (Double.isInfinite(scores[i]) && scores[i] < 0.0) {
                continue;
            }

            maiorScore = Math.max(maiorScore, scores[i]);
        }

        if (Double.isInfinite(maiorScore)) {
            // Se tudo falhou, a roleta cai no fallback de seguranca.
            pesos[0] = 1.0;
            return pesos;
        }

        double soma = 0.0;
        for (int i = 0; i < scores.length; i++) {
            if (Double.isInfinite(scores[i]) && scores[i] < 0.0) {
                pesos[i] = 0.0;
                continue;
            }

            // Softmax estabilizado: comparar tudo com o maior score evita explosao numerica.
            pesos[i] = Math.exp(scores[i] - maiorScore) + EPSILON;
            soma += pesos[i];
        }

        for (int i = 0; i < pesos.length; i++) {
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
        return Integer.toHexString(System.identityHashCode(this));
    }

    private String nomeAcao(int codigo) {
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
        return "(" + posicao.x + "," + posicao.y + ")";
    }

    private String formatarScore(double score) {
        if (Double.isInfinite(score) && score < 0.0) {
            return "-INF";
        }
        return formatarDouble(score);
    }

    private String formatarDouble(double valor) {
        return String.format(java.util.Locale.US, "%.3f", valor);
    }

    private void registrarVisita(Point posicao) {
        Point chave = new Point(posicao);
        Integer visitas = lugaresVisitados.get(chave);
        // O mapa de visitas hoje alimenta observabilidade; pode virar heuristica no futuro.
        lugaresVisitados.put(chave, Integer.valueOf(visitas == null ? 1 : visitas.intValue() + 1));
    }

    private static boolean isDestinoValido(int celula) {
        if (celula == Constantes.foraAmbiene || celula == Constantes.semVisao || celula == Constantes.numeroParede) {
            return false;
        }

        if (celula == Constantes.numeroPoupador01 || celula == Constantes.numeroPoupador02) {
            return false;
        }

        return !isLadrao(celula);
    }

    private static boolean isLadrao(int celula) {
        return celula >= Constantes.numeroLadrao01 && celula <= Constantes.numeroLadrao04;
    }

    private static double intensidadeOlfato(int marca) {
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
            this.codigo = codigo;
            this.dx = dx;
            this.dy = dy;
            this.indiceVisao = indiceVisao;
        }

        private boolean isParado() {
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
            this.visao = visao;
            this.olfatoLadrao = olfatoLadrao;
            this.posicao = new Point(posicao);
            this.numeroMoedas = numeroMoedas;
            this.numeroJogadasImunes = numeroJogadasImunes;
            this.lugaresVisitados = lugaresVisitados;
            this.permanenciasConsecutivas = permanenciasConsecutivas;
        }

        private boolean isDestinoValido(Acao acao) {
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
            return new Point(posicao);
        }

        private int getPermanenciasConsecutivas() {
            return permanenciasConsecutivas;
        }

        private int getVisitas(Acao acao) {
            Integer visitas = lugaresVisitados.get(getPosicaoDestino(acao));
            return visitas == null ? 0 : visitas.intValue();
        }

        private Point getPosicaoDestino(Acao acao) {
            return new Point(posicao.x + acao.dx, posicao.y + acao.dy);
        }

        private double getCargaFinanceira() {
            // Normaliza a carga de moedas no intervalo [0, 1].
            return Math.min(1.0, numeroMoedas / 10.0);
        }

        private double getImunidade() {
            return Math.min(1.0, numeroJogadasImunes / (double) Constantes.numeroTICsImunes);
        }

        private double getCapacidadeCompraPastilha() {
            return numeroMoedas >= Constantes.custoPastinha ? 1.0 : 0.0;
        }

        private double getVulnerabilidade() {
            return 1.0 - getImunidade();
        }

        private double getUrgenciaPastilha() {
            return getCapacidadeCompraPastilha() * getVulnerabilidade() * getPressaoRisco();
        }

        private double getPressaoRisco() {
            // Usa o pior risco imediato do estado parado como referencia do tick, ja normalizado.
            return Math.max(getRiscoVisualNormalizado(ACOES[0]), getRiscoOlfativoNormalizado(ACOES[0]));
        }

        private double getRelevanciaMoeda() {
            return Math.max(0.0, (1.0 - getPressaoRisco()) * (1.0 - getUrgenciaBanco()));
        }

        private double getUrgenciaBanco() {
            double carga = getCargaFinanceira();
            // Curva convexa normalizada: carregar mais moedas acelera a vontade de bancar em [0, 1].
            return (carga * (1.0 + carga)) / 2.0;
        }

        private double getAtracaoBanco(Acao acao) {
            if (isDestinoBanco(acao)) {
                return 1.0;
            }

            // Fora do deposito real, o banco entra como atracao geometrica por distancia Manhattan.
            Point destino = getPosicaoDestino(acao);
            int distancia = Math.abs(destino.x - Constantes.posicaoBanco.x) + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return 1.0 / (distancia + 1.0);
        }

        private double getProximidadeLocalBanco(Acao acao) {
            if (isDestinoBanco(acao)) {
                return 1.0;
            }

            Point destino = getPosicaoDestino(acao);
            int distancia = Math.abs(destino.x - Constantes.posicaoBanco.x) + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return distancia <= 2 ? (1.0 / (distancia + 1.0)) : 0.0;
        }

        private double getObjetivoBanco(Acao acao) {
            if (getBonusDeposito(acao) > 0.0) {
                return 1.0;
            }

            double progresso = Math.max(0.0, getProgressoBanco(acao));
            double proximidade = getProximidadeLocalBanco(acao);
            return (progresso + proximidade) / 2.0;
        }

        private double getProgressoBanco(Acao acao) {
            // Mede se a acao encurta o caminho ate a posicao do banco.
            int distanciaAtual = Math.abs(posicao.x - Constantes.posicaoBanco.x)
                + Math.abs(posicao.y - Constantes.posicaoBanco.y);
            Point destino = getPosicaoDestino(acao);
            int distanciaDestino = Math.abs(destino.x - Constantes.posicaoBanco.x)
                + Math.abs(destino.y - Constantes.posicaoBanco.y);
            return distanciaAtual - distanciaDestino;
        }

        private double getBonusDeposito(Acao acao) {
            // O bonus so existe quando a acao realmente aponta para a celula banco.
            return isDestinoBanco(acao) && numeroMoedas > 0 ? 1.0 : 0.0;
        }

        private boolean isDestinoBanco(Acao acao) {
            return !acao.isParado() && visao[acao.indiceVisao] == Constantes.numeroBanco;
        }

        private double getAtracaoVisivel(Acao acao, int tipoAlvo) {
            double atracao = 0.0;

            for (int i = 0; i < visao.length; i++) {
                if (visao[i] != tipoAlvo) {
                    continue;
                }

                int[] offset = OFFSETS_VISAO[i];
                // Quanto mais a acao se alinha com o alvo visivel, maior a atracao acumulada.
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

            return getAtracaoVisivel(acao, tipoAlvo) / maior;
        }

        private double getMaiorAtracaoVisivel(int tipoAlvo) {
            double maior = 0.0;

            for (int i = 0; i < ACOES.length; i++) {
                maior = Math.max(maior, getAtracaoVisivel(ACOES[i], tipoAlvo));
            }

            return maior;
        }


        private double calcularRiscoVisual(Acao acao) {
            double risco = 0.0;
            double vulnerabilidade = getVulnerabilidade();

            for (int i = 0; i < visao.length; i++) {
                if (!isLadrao(visao[i])) {
                    continue;
                }

                int[] offset = OFFSETS_VISAO[i];
                // Ladrado perto pesa mais; imunidade reduz a exposicao efetiva.
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                risco += vulnerabilidade / Math.max(1.0, distancia);
            }

            return risco;
        }

        private double getRiscoVisualNormalizado(Acao acao) {
            return normalizarRisco(calcularRiscoVisual(acao));
        }

        private boolean isCapturaImediataEvitable(Acao acao) {
            if (getImunidade() > 0.0) {
                return false;
            }

            // Primeiro aplica cortes simples por distancia visual ao ladrado.
            int melhorDistancia = melhorDistanciaVisualSegura();
            int distanciaAcao = menorDistanciaVisual(acao);

            if (melhorDistancia >= 3 && distanciaAcao <= 2) {
                return true;
            }

            if (melhorDistancia >= 2 && distanciaAcao <= 1) {
                return true;
            }

            // Depois compara a acao com a alternativa admissivel de menor risco composto.
            double melhorRisco = melhorRiscoCompostoSeguro();
            double riscoAcao = calcularRiscoComposto(acao);
            return distanciaAcao <= 2 && (riscoAcao - melhorRisco) >= 0.75;
        }

        private int melhorDistanciaVisualSegura() {
            int melhor = Integer.MIN_VALUE;

            for (int i = 0; i < ACOES.length; i++) {
                Acao candidata = ACOES[i];
                if (!isDestinoValido(candidata)) {
                    continue;
                }

                melhor = Math.max(melhor, menorDistanciaVisual(candidata));
            }

            return melhor == Integer.MIN_VALUE ? Integer.MAX_VALUE : melhor;
        }

        private int menorDistanciaVisual(Acao acao) {
            int menor = Integer.MAX_VALUE;

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

        private double melhorRiscoCompostoSeguro() {
            double melhor = Double.POSITIVE_INFINITY;

            for (int i = 0; i < ACOES.length; i++) {
                Acao candidata = ACOES[i];
                if (!isDestinoValido(candidata)) {
                    continue;
                }

                melhor = Math.min(melhor, calcularRiscoComposto(candidata));
            }

            return Double.isInfinite(melhor) ? Double.POSITIVE_INFINITY : melhor;
        }

        private double calcularRiscoComposto(Acao acao) {
            // O olfato entra como desempate mais fraco que a visao.
            return calcularRiscoVisual(acao) + (0.5 * calcularRiscoOlfativo(acao));
        }

        private double calcularRiscoOlfativo(Acao acao) {
            double risco = 0.0;

            for (int i = 0; i < olfatoLadrao.length; i++) {
                double intensidade = intensidadeOlfato(olfatoLadrao[i]);
                if (intensidade == 0.0) {
                    continue;
                }

                int[] offset = OFFSETS_OLFATO[i];
                // O cheiro e tratado como risco difuso de curto alcance.
                int distancia = Math.abs(acao.dx - offset[0]) + Math.abs(acao.dy - offset[1]);
                risco += intensidade / (distancia + 1.0);
            }

            return risco;
        }

        private double getRiscoOlfativoNormalizado(Acao acao) {
            return normalizarRisco(calcularRiscoOlfativo(acao));
        }

        private double getEstagnacaoNormalizada() {
            return permanenciasConsecutivas <= 0 ? 0.0 : permanenciasConsecutivas / (permanenciasConsecutivas + 1.0);
        }

        private double normalizarRisco(double risco) {
            return risco <= 0.0 ? 0.0 : risco / (1.0 + risco);
        }
    }
}
