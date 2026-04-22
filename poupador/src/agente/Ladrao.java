package agente;

import algoritmo.ProgramaLadrao;
import java.awt.Point;
import java.util.HashMap;

public class Ladrao extends ProgramaLadrao {

	// Memória de visitas  (Modelo de Mundo)
	private HashMap<String, Integer> visitas = new HashMap<String, Integer>();

	// Contador total de passos para calcular estatística (Frequência Relativa)
	private int totalPassos = 0;

	// DICIONÁRIO DE INSTINTOS (Campos Potenciais Artificiais)
	// A matemática emergente: 1 Poupador (1.0) + 2 Ladrões (-1.2) = -0.2 (Campo Negativo).
	// Isto impede autonomamente que mais de 2 ladrões persigam o mesmo alvo.
	private HashMap<Integer, Double> instintosNaturais = new HashMap<Integer, Double>() {{
		// FAMÍLIA DOS POUPADORES (Todos geram atração matemática)
		put(100, 1.0);   // Poupador 0
		put(110, 1.0);   // Poupador 1
		put(120, 1.0);   // Poupador 2
		put(130, 1.0);   // Poupador 3

		// FAMÍLIA DOS LADRÕES (Repulsa calibrada para permitir máximo de 2 no mesmo alvo)
		put(200, -0.6);  // Ladrão 0
		put(210, -0.6);  // Ladrão 1
		put(220, -0.6);  // Ladrão 2
		put(230, -0.6);  // Ladrão 3
	}};

	public int acao() {
		try {
			totalPassos++;

			int[] visao = sensor.getVisaoIdentificacao();
			int[] faroPoupador = sensor.getAmbienteOlfatoPoupador();
			int[] faroLadrao = sensor.getAmbienteOlfatoLadrao();
			Point pos = sensor.getPosicao();

			if (visao == null || faroPoupador == null || faroLadrao == null || pos == null) return 0;

			int[] indexVisao = {0, 7, 16, 12, 11};
			int[] indexFaro = {0, 1, 6, 4, 3};

			// 1. FILTRO DE FÍSICA (Limites intransponíveis do ambiente)
			int[] direcoesValidas = new int[5];
			int totalValidas = 0;
			for (int d = 1; d <= 4; d++) {
				if (indexVisao[d] < visao.length) {
					int v = visao[indexVisao[d]];
					// Filtra paredes e obstáculos sólidos
					if (v != 1 && v != -1 && v != -2 && v != 3 && v != 4 && v != 5) {
						direcoesValidas[totalValidas] = d;
						totalValidas++;
					}
				}
			}

			if (totalValidas == 0) return 0;

			// 2. PESOS DA TEORIA DA UTILIDADE ESPERADA
			double pesoVisao = 2.0;
			double pesoOlfato = 1.0;
			double pesoMemoria = 1.5;

			// 3. CÁLCULO DE UTILIDADE: OLFATO E MEMÓRIA
			double[] utilidades = new double[5];
			for (int d = 1; d <= 4; d++) utilidades[d] = -9999999.0;

			for (int i = 0; i < totalValidas; i++) {
				int dir = direcoesValidas[i];
				utilidades[dir] = Math.random() * 0.01;

				int faroP = faroPoupador[indexFaro[dir]];
				int faroL = faroLadrao[indexFaro[dir]];

				// Aplicamos a mesma proporção de Campo Potencial (0.6) ao olfato de outros ladrões
				if (faroP > 0) utilidades[dir] += pesoOlfato * (1.0 / faroP);
				if (faroL > 0) utilidades[dir] -= pesoOlfato * 0.6 * (1.0 / faroL);

				int numVisitas = obterVisitas(pos, dir);
				double frequenciaRelativa = (double) numVisitas / totalPassos;
				utilidades[dir] -= pesoMemoria * frequenciaRelativa;
			}

			// 4. O PREDADOR VISUAL (Soma de Forças Vetoriais)
			for (int i = 0; i < visao.length; i++) {
				int v = visao[i];

				if (instintosNaturais.containsKey(v)) {

					int map_i = (i < 12) ? i : i + 1;
					int row = map_i / 5;
					int col = map_i % 5;

					double distanciaManhattan = Math.abs(row - 2) + Math.abs(col - 2);
					if (distanciaManhattan == 0) distanciaManhattan = 0.1;

					double multiplicador = instintosNaturais.get(v);
					double forcaCalculada = pesoVisao * (1.0 / distanciaManhattan) * multiplicador;

					if (row < 2 && utilidades[1] > -999999.0) utilidades[1] += forcaCalculada; // Cima
					if (row > 2 && utilidades[2] > -999999.0) utilidades[2] += forcaCalculada; // Baixo
					if (col > 2 && utilidades[3] > -999999.0) utilidades[3] += forcaCalculada; // Direita
					if (col < 2 && utilidades[4] > -999999.0) utilidades[4] += forcaCalculada; // Esquerda
				}
			}

			// 5. TOMADA DE DECISÃO FINAL (Argmax puro)
			int melhorAcao = 0;
			double maxUtilidade = -9999999.0;

			for (int dir = 1; dir <= 4; dir++) {
				if (utilidades[dir] > maxUtilidade && utilidades[dir] > -999999.0) {
					maxUtilidade = utilidades[dir];
					melhorAcao = dir;
				}
			}

			registrarVisita(pos);
			// Retorna o movimento ótimo matematicamente calculado. Fallback para 0 garante que nunca congele.
			return melhorAcao != 0 ? melhorAcao : direcoesValidas[0];

		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	private void registrarVisita(Point pos) {
		if (pos == null) return;
		String chave = pos.x + "," + pos.y;
		Integer count = visitas.get(chave);
		visitas.put(chave, (count == null ? 0 : count) + 1);
	}

	private int obterVisitas(Point pos, int dir) {
		if (pos == null) return 0;
		int nx = pos.x; int ny = pos.y;
		if (dir == 1) ny--; else if (dir == 2) ny++; else if (dir == 3) nx++; else if (dir == 4) nx--;
		Integer count = visitas.get(nx + "," + ny);
		return (count == null) ? 0 : count;
	}
}