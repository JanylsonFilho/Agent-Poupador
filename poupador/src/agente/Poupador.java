package agente;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import algoritmo.ProgramaPoupador;
import controle.Constantes;

public class Poupador extends ProgramaPoupador {
    private static final double TOLERANCIA_RISCO = 1.0e-9;

    private static final double PESO_BASE_SEGURANCA = 0.35;
    private static final double PESO_BASE_OLFATO = 0.25;
    private static final double PESO_BASE_VISITACAO = 0.15;
    private static final double PESO_BASE_BANCO = 0.15;
    private static final double PESO_BASE_MOEDA = 0.07;
    private static final double PESO_BASE_PASTILHA = 0.03;

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

    private static final Movimento[] MOVIMENTOS = new Movimento[] {
        new Movimento(1, 0, -1, 7),
        new Movimento(2, 0, 1, 16),
        new Movimento(3, 1, 0, 12),
        new Movimento(4, -1, 0, 11)
    };

    private final Map<Point, Integer> lugaresVisitados = new HashMap<Point, Integer>();

    private final Heuristica[] heuristicas = new Heuristica[] {
        new HeuristicaSeguranca(),
        new HeuristicaOlfato(),
        new HeuristicaVisitacao(),
        new HeuristicaBanco(),
        new HeuristicaMoeda(),
        new HeuristicaPastilha()
    };

    public int acao() {
        registrarVisita(sensor.getPosicao());

        Contexto contexto = new Contexto(
            sensor.getVisaoIdentificacao(),
            sensor.getAmbienteOlfatoLadrao(),
            sensor.getPosicao(),
            sensor.getNumeroDeMoedas(),
            sensor.getNumeroJogadasImunes(),
            lugaresVisitados
        );

        double[] roletaBase = Roleta.criarBase(contexto);
        if (Roleta.soma(roletaBase) == 0.0) {
            return 0;
        }

        ResultadoHeuristica[] resultados = new ResultadoHeuristica[heuristicas.length];
        for (int i = 0; i < heuristicas.length; i++) {
            resultados[i] = heuristicas[i].calcular(contexto);
        }

        double[] roletaFinal = Roleta.combinar(roletaBase, resultados);
        return Roleta.sortear(roletaFinal);
    }

    private void registrarVisita(Point posicao) {
        Point chave = new Point(posicao);
        Integer visitas = lugaresVisitados.get(chave);

        if (visitas == null) {
            lugaresVisitados.put(chave, Integer.valueOf(1));
            return;
        }

        lugaresVisitados.put(chave, Integer.valueOf(visitas.intValue() + 1));
    }

    private static boolean isDestinoValido(int celula) {
        if (celula == Constantes.foraAmbiene || celula == Constantes.semVisao || celula == Constantes.numeroParede) {
            return false;
        }

        if (celula != Constantes.numeroPoupador01 && celula != Constantes.numeroPoupador02) {
            return !isLadrao(celula);
        }

        return false;
    }

    private static boolean isLadrao(int celula) {
        return celula >= Constantes.numeroLadrao01 && celula <= Constantes.numeroLadrao04;
    }

    private static boolean isMoeda(int celula) {
        return celula == Constantes.numeroMoeda;
    }

    private static boolean isPastilha(int celula) {
        return celula == Constantes.numeroPastinhaPoder;
    }

    private static double calcularAmeacaPorDistancia(int distancia) {
        return 1.0 / (distancia + 1.0);
    }

    private static double calcularIntensidadeOlfato(int marca) {
        if (marca <= 0) {
            return 0.0;
        }

        return (6.0 - marca) / 5.0;
    }

    private static double[] normalizarPeloMaior(double[] valores, boolean[] movimentosValidos) {
        double maior = 0.0;
        double[] normalizados = new double[valores.length];

        for (int i = 0; i < valores.length; i++) {
            if (!movimentosValidos[i]) {
                continue;
            }

            maior = Math.max(maior, valores[i]);
        }

        if (maior == 0.0) {
            return normalizados;
        }

        for (int i = 0; i < valores.length; i++) {
            if (!movimentosValidos[i]) {
                continue;
            }

            normalizados[i] = valores[i] / maior;
        }

        return normalizados;
    }

    private static double[] misturarPreferencia(double[] preferencia, boolean[] movimentosValidos, double relevancia) {
        double[] pesos = new double[MOVIMENTOS.length];

        for (int i = 0; i < MOVIMENTOS.length; i++) {
            if (!movimentosValidos[i]) {
                pesos[i] = 0.0;
                continue;
            }

            pesos[i] = (1.0 - relevancia) + (relevancia * preferencia[i]);
        }

        return pesos;
    }

    private static double[] calcularAtracaoPorAlvoVisivel(Contexto contexto, int tipoAlvo) {
        double[] atracoes = new double[MOVIMENTOS.length];

        for (int i = 0; i < MOVIMENTOS.length; i++) {
            if (!contexto.isMovimentoValido(i)) {
                continue;
            }

            Movimento movimento = contexto.getMovimento(i);
            double atracao = 0.0;

            for (int indiceVisao = 0; indiceVisao < contexto.getVisao().length; indiceVisao++) {
                if (contexto.getCelulaVisao(indiceVisao) != tipoAlvo) {
                    continue;
                }

                int[] offsetAlvo = OFFSETS_VISAO[indiceVisao];
                int distancia = Math.abs(movimento.getDx() - offsetAlvo[0]) + Math.abs(movimento.getDy() - offsetAlvo[1]);
                atracao += 1.0 / (distancia + 1.0);
            }

            atracoes[i] = atracao;
        }

        return atracoes;
    }

    private interface Heuristica {
        ResultadoHeuristica calcular(Contexto contexto);
    }

    private static final class HeuristicaSeguranca implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] riscosVisuais = contexto.calcularRiscosVisuais();
            double[] pesos = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!contexto.isMovimentoValido(i)) {
                    continue;
                }

                pesos[i] = 1.0 - riscosVisuais[i];
            }

            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + (2.0 * maiorValor(riscosVisuais));
            double peso = PESO_BASE_SEGURANCA * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class HeuristicaOlfato implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] riscosOlfativos = contexto.calcularRiscosOlfativos();
            double[] pesos = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!contexto.isMovimentoValido(i)) {
                    continue;
                }

                pesos[i] = 1.0 - riscosOlfativos[i];
            }

            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + maiorValor(riscosOlfativos);
            double peso = PESO_BASE_OLFATO * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class HeuristicaVisitacao implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] pesos = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!contexto.isMovimentoValido(i)) {
                    continue;
                }

                pesos[i] = 1.0 / (contexto.getVisitasProximaPosicao(i) + 1.0);
            }

            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + (1.0 - contexto.getPressaoRisco());
            double peso = PESO_BASE_VISITACAO * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class HeuristicaBanco implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] aproximacoes = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!contexto.isMovimentoValido(i)) {
                    continue;
                }

                int distanciaBanco = contexto.getDistanciaAteBanco(i);
                aproximacoes[i] = 1.0 / (distanciaBanco + 1.0);
            }

            double[] preferencia = normalizarPeloMaior(aproximacoes, contexto.getMovimentosValidos());
            double relevancia = contexto.getCargaFinanceira();
            double[] pesos = misturarPreferencia(preferencia, contexto.getMovimentosValidos(), relevancia);
            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + contexto.getCargaFinanceira();
            double peso = PESO_BASE_BANCO * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class HeuristicaMoeda implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] atracoes = calcularAtracaoPorAlvoVisivel(contexto, Constantes.numeroMoeda);
            double[] preferencia = normalizarPeloMaior(atracoes, contexto.getMovimentosValidos());
            double relevancia = maiorValor(preferencia);
            double[] pesos = misturarPreferencia(preferencia, contexto.getMovimentosValidos(), relevancia);
            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + ((1.0 - contexto.getPressaoRisco()) * (1.0 - contexto.getCargaFinanceira()));
            double peso = PESO_BASE_MOEDA * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class HeuristicaPastilha implements Heuristica {
        @Override
        public ResultadoHeuristica calcular(Contexto contexto) {
            double[] atracoes = calcularAtracaoPorAlvoVisivel(contexto, Constantes.numeroPastinhaPoder);
            double[] preferencia = normalizarPeloMaior(atracoes, contexto.getMovimentosValidos());
            double vulnerabilidade = contexto.getVulnerabilidade();
            double compraDisponivel = contexto.getCapacidadeCompraPastilha();
            double relevancia = compraDisponivel * vulnerabilidade * maiorValor(preferencia);
            double[] pesos = misturarPreferencia(preferencia, contexto.getMovimentosValidos(), relevancia);
            double[] roleta = Roleta.normalizar(pesos);
            double contraste = Roleta.calcularContraste(roleta, contexto.getMovimentosValidos());
            double ganho = 1.0 + vulnerabilidade;
            double peso = PESO_BASE_PASTILHA * contraste * ganho;
            return new ResultadoHeuristica(roleta, peso);
        }
    }

    private static final class Contexto {
        private final int[] visao;
        private final int[] olfatoLadrao;
        private final Point posicao;
        private final int numeroMoedas;
        private final int numeroJogadasImunes;
        private final Map<Point, Integer> lugaresVisitados;
        private final boolean[] movimentosValidos;

        private Contexto(int[] visao, int[] olfatoLadrao, Point posicao, int numeroMoedas, int numeroJogadasImunes,
                Map<Point, Integer> lugaresVisitados) {
            this.visao = visao;
            this.olfatoLadrao = olfatoLadrao;
            this.posicao = new Point(posicao);
            this.numeroMoedas = numeroMoedas;
            this.numeroJogadasImunes = numeroJogadasImunes;
            this.lugaresVisitados = lugaresVisitados;
            this.movimentosValidos = calcularMovimentosValidos(visao);
        }

        private boolean[] calcularMovimentosValidos(int[] visaoAtual) {
            boolean[] validos = new boolean[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                validos[i] = isDestinoValido(visaoAtual[MOVIMENTOS[i].getIndiceVisao()]);
            }

            return validos;
        }

        private int[] getVisao() {
            return visao;
        }

        private int getCelulaVisao(int indice) {
            return visao[indice];
        }

        private int[] getOlfatoLadrao() {
            return olfatoLadrao;
        }

        private int getMarcaOlfatoLadrao(int indice) {
            return olfatoLadrao[indice];
        }

        private boolean[] getMovimentosValidos() {
            return movimentosValidos;
        }

        private boolean isMovimentoValido(int indiceMovimento) {
            return movimentosValidos[indiceMovimento];
        }

        private Movimento getMovimento(int indiceMovimento) {
            return MOVIMENTOS[indiceMovimento];
        }

        private Point getProximaPosicao(int indiceMovimento) {
            Movimento movimento = getMovimento(indiceMovimento);
            return new Point(posicao.x + movimento.getDx(), posicao.y + movimento.getDy());
        }

        private int getVisitasProximaPosicao(int indiceMovimento) {
            Integer visitas = lugaresVisitados.get(getProximaPosicao(indiceMovimento));
            if (visitas == null) {
                return 0;
            }

            return visitas.intValue();
        }

        private int getDistanciaAteBanco(int indiceMovimento) {
            Point proximaPosicao = getProximaPosicao(indiceMovimento);
            return Math.abs(proximaPosicao.x - Constantes.posicaoBanco.x) + Math.abs(proximaPosicao.y - Constantes.posicaoBanco.y);
        }

        private double[] calcularRiscosVisuais() {
            double[] riscos = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!isMovimentoValido(i)) {
                    riscos[i] = 1.0;
                    continue;
                }

                double produtoSeguranca = 1.0;
                Movimento movimento = getMovimento(i);

                for (int indiceVisao = 0; indiceVisao < visao.length; indiceVisao++) {
                    if (!isLadrao(visao[indiceVisao])) {
                        continue;
                    }

                    int[] offsetLadrao = OFFSETS_VISAO[indiceVisao];
                    int distancia = Math.abs(movimento.getDx() - offsetLadrao[0]) + Math.abs(movimento.getDy() - offsetLadrao[1]);
                    double ameaca = calcularAmeacaSeguranca(distancia);
                    produtoSeguranca *= (1.0 - ameaca);
                }

                riscos[i] = 1.0 - produtoSeguranca;
            }

            return riscos;
        }

        private double[] calcularRiscosOlfativos() {
            double[] riscos = new double[MOVIMENTOS.length];

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!isMovimentoValido(i)) {
                    riscos[i] = 1.0;
                    continue;
                }

                double produtoSeguranca = 1.0;
                Movimento movimento = getMovimento(i);

                for (int indiceOlfato = 0; indiceOlfato < olfatoLadrao.length; indiceOlfato++) {
                    double intensidade = calcularIntensidadeOlfato(olfatoLadrao[indiceOlfato]);
                    if (intensidade == 0.0) {
                        continue;
                    }

                    int[] offsetCheiro = OFFSETS_OLFATO[indiceOlfato];
                    int distancia = Math.abs(movimento.getDx() - offsetCheiro[0]) + Math.abs(movimento.getDy() - offsetCheiro[1]);
                    double ameaca = intensidade * calcularAmeacaPorDistancia(distancia);
                    produtoSeguranca *= (1.0 - ameaca);
                }

                riscos[i] = 1.0 - produtoSeguranca;
            }

            return riscos;
        }

        private boolean[] getMovimentosSegurosPrioritarios() {
            boolean[] permitidos = new boolean[MOVIMENTOS.length];
            double[] riscosVisuais = calcularRiscosVisuais();
            double menorRiscoVisual = Double.POSITIVE_INFINITY;

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!isMovimentoValido(i)) {
                    continue;
                }

                menorRiscoVisual = Math.min(menorRiscoVisual, riscosVisuais[i]);
            }

            if (Double.isInfinite(menorRiscoVisual)) {
                return permitidos;
            }

            int quantidadePermitidos = 0;
            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!isMovimentoValido(i)) {
                    continue;
                }

                if (riscosVisuais[i] <= (menorRiscoVisual + TOLERANCIA_RISCO)) {
                    permitidos[i] = true;
                    quantidadePermitidos++;
                }
            }

            if (quantidadePermitidos <= 1) {
                return permitidos;
            }

            double[] riscosOlfativos = calcularRiscosOlfativos();
            double menorRiscoOlfativo = Double.POSITIVE_INFINITY;

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!permitidos[i]) {
                    continue;
                }

                menorRiscoOlfativo = Math.min(menorRiscoOlfativo, riscosOlfativos[i]);
            }

            boolean[] refinados = new boolean[MOVIMENTOS.length];
            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!permitidos[i]) {
                    continue;
                }

                refinados[i] = riscosOlfativos[i] <= (menorRiscoOlfativo + TOLERANCIA_RISCO);
            }

            return refinados;
        }

        private double calcularAmeacaSeguranca(int distancia) {
            double vulnerabilidade = 1.0 - getImunidadeNormalizada();
            if (vulnerabilidade == 0.0) {
                return 0.0;
            }

            if (distancia <= 1) {
                return vulnerabilidade;
            }

            return vulnerabilidade * (1.0 / distancia);
        }

        private double getCargaFinanceira() {
            return Math.min(1.0, numeroMoedas / 10.0);
        }

        private double getImunidadeNormalizada() {
            return Math.min(1.0, numeroJogadasImunes / (double) Constantes.numeroTICsImunes);
        }

        private double getCapacidadeCompraPastilha() {
            return Math.min(1.0, Math.floor(numeroMoedas / (double) Constantes.custoPastinha));
        }

        private double getPressaoRisco() {
            return Math.max(calcularPressaoVisualAtual(), calcularPressaoOlfativaAtual());
        }

        private double getVulnerabilidade() {
            return (0.5 * (1.0 - getImunidadeNormalizada())) + (0.3 * getPressaoRisco()) + (0.2 * getCargaFinanceira());
        }

        private double calcularPressaoVisualAtual() {
            double maior = 0.0;

            for (int indiceVisao = 0; indiceVisao < visao.length; indiceVisao++) {
                if (!isLadrao(visao[indiceVisao])) {
                    continue;
                }

                int[] offsetLadrao = OFFSETS_VISAO[indiceVisao];
                int distancia = Math.abs(offsetLadrao[0]) + Math.abs(offsetLadrao[1]);
                maior = Math.max(maior, calcularAmeacaPorDistancia(distancia));
            }

            return maior;
        }

        private double calcularPressaoOlfativaAtual() {
            double maior = 0.0;

            for (int i = 0; i < olfatoLadrao.length; i++) {
                maior = Math.max(maior, calcularIntensidadeOlfato(olfatoLadrao[i]));
            }

            return maior;
        }
    }

    private static final class ResultadoHeuristica {
        private final double[] roleta;
        private final double peso;

        private ResultadoHeuristica(double[] roleta, double peso) {
            this.roleta = roleta;
            this.peso = peso;
        }

        private double[] getRoleta() {
            return roleta;
        }

        private double getPeso() {
            return peso;
        }
    }

    private static final class Roleta {
        private Roleta() {
        }

        private static double[] criarBase(Contexto contexto) {
            double[] pesos = new double[MOVIMENTOS.length];
            boolean[] movimentosPriorizados = contexto.getMovimentosSegurosPrioritarios();
            boolean existeMovimentoPriorizado = false;

            for (int i = 0; i < movimentosPriorizados.length; i++) {
                if (movimentosPriorizados[i]) {
                    existeMovimentoPriorizado = true;
                    break;
                }
            }

            for (int i = 0; i < MOVIMENTOS.length; i++) {
                if (!contexto.isMovimentoValido(i)) {
                    pesos[i] = 0.0;
                    continue;
                }

                pesos[i] = (!existeMovimentoPriorizado || movimentosPriorizados[i]) ? 1.0 : 0.0;
            }

            return normalizar(pesos);
        }

        private static double[] combinar(double[] roletaBase, ResultadoHeuristica[] resultados) {
            double[] pesosCombinados = new double[MOVIMENTOS.length];
            double somaPesos = 0.0;

            for (int i = 0; i < resultados.length; i++) {
                double peso = resultados[i].getPeso();
                if (peso == 0.0) {
                    continue;
                }

                somaPesos += peso;
                double[] roletaHeuristica = resultados[i].getRoleta();
                for (int j = 0; j < pesosCombinados.length; j++) {
                    pesosCombinados[j] += peso * roletaHeuristica[j];
                }
            }

            if (somaPesos == 0.0) {
                return roletaBase;
            }

            for (int i = 0; i < pesosCombinados.length; i++) {
                pesosCombinados[i] /= somaPesos;
            }

            double pesoSeguranca = resultados[0].getPeso();
            if (pesoSeguranca > 0.0) {
                double[] roletaSeguranca = resultados[0].getRoleta();
                for (int i = 0; i < pesosCombinados.length; i++) {
                    pesosCombinados[i] *= roletaSeguranca[i];
                }
            }

            for (int i = 0; i < pesosCombinados.length; i++) {
                pesosCombinados[i] *= roletaBase[i];
            }

            if (soma(pesosCombinados) == 0.0) {
                return roletaBase;
            }

            return normalizar(pesosCombinados);
        }

        private static double calcularContraste(double[] roleta, boolean[] movimentosValidos) {
            double maior = Double.NEGATIVE_INFINITY;
            double menor = Double.POSITIVE_INFINITY;
            int quantidadeValidos = 0;

            for (int i = 0; i < roleta.length; i++) {
                if (!movimentosValidos[i]) {
                    continue;
                }

                quantidadeValidos++;
                maior = Math.max(maior, roleta[i]);
                menor = Math.min(menor, roleta[i]);
            }

            if (quantidadeValidos <= 1) {
                return 0.0;
            }

            return maior - menor;
        }

        private static double[] normalizar(double[] pesos) {
            double soma = soma(pesos);
            double[] normalizados = new double[pesos.length];

            if (soma == 0.0) {
                return normalizados;
            }

            for (int i = 0; i < pesos.length; i++) {
                normalizados[i] = pesos[i] / soma;
            }

            return normalizados;
        }

        private static double soma(double[] valores) {
            double soma = 0.0;

            for (int i = 0; i < valores.length; i++) {
                soma += valores[i];
            }

            return soma;
        }

        private static int sortear(double[] roleta) {
            double sorteio = Math.random();
            double acumulado = 0.0;

            for (int i = 0; i < roleta.length; i++) {
                acumulado += roleta[i];
                if (sorteio <= acumulado) {
                    return MOVIMENTOS[i].getAcao();
                }
            }

            return MOVIMENTOS[MOVIMENTOS.length - 1].getAcao();
        }
    }

    private static double maiorValor(double[] valores) {
        double maior = 0.0;

        for (int i = 0; i < valores.length; i++) {
            maior = Math.max(maior, valores[i]);
        }

        return maior;
    }

    private static final class Movimento {
        private final int acao;
        private final int dx;
        private final int dy;
        private final int indiceVisao;

        private Movimento(int acao, int dx, int dy, int indiceVisao) {
            this.acao = acao;
            this.dx = dx;
            this.dy = dy;
            this.indiceVisao = indiceVisao;
        }

        private int getAcao() {
            return acao;
        }

        private int getDx() {
            return dx;
        }

        private int getDy() {
            return dy;
        }

        private int getIndiceVisao() {
            return indiceVisao;
        }
    }
}
