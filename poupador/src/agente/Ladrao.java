package agente;

import algoritmo.ProgramaLadrao;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Classe HScore responsável pelo cálculo da heurística de distância.
 * Utiliza a métrica de Manhattan Distance para estimar a distância entre dois pontos
 * em um espaço bidimensional (labirinto 30x30).
 * Esta heurística é fundamental para otimizar o algoritmo A*, permitindo uma busca
 * mais rápida e eficiente no espaço de estados.
 */
class HScore {
	/**
	 * Calcula a distância de Manhattan entre dois pontos no labirinto.
	 * Fórmula: |x1 - x2| + |y1 - y2|
	 * Esta métrica representa o número mínimo de movimentos necessários para alcançar
	 * um ponto a outro, considerando apenas movimentos horizontais e verticais (sem diagonais).
	 *
	 * @param originCoordinates Array [x, y] do ponto de origem.
	 * @param destinyCoordinates Array [x, y] do ponto de destino.
	 * @return A distância de Manhattan entre os dois pontos.
	 */
	public static int calculateManhattanDistance(int[] originCoordinates, int[] destinyCoordinates) {
		return Math.abs(originCoordinates[0] - destinyCoordinates[0])
				+ Math.abs(originCoordinates[1] - destinyCoordinates[1]);
	}
}

/**
 * Classe Node representa um nó na árvore de busca do algoritmo A*.
 * Cada nó contém um rótulo (representando uma coordenada no labirinto)
 * e uma referência ao seu nó raiz/pai, formando uma estrutura de árvore que
 * permite reconstruir o caminho da origem até o destino.
 */
class Node {
	// O rótulo único do vértice no formato "x:y" (coordenadas do ponto no labirinto).
	public String label;
	// Referência ao nó pai/raiz na árvore de busca (permite reconstruir o caminho).
	public Node root;

	// Construtor que inicializa um nó com seu rótulo e nó pai.
	Node(String label, Node root) {
		this.label = label;
		this.root = root;
	}
}

/**
 * Classe Graph representa a estrutura de grafo do labirinto.
 * O grafo é construído dinamicamente com base na visão ou memória do Ladrão,
 * contendo apenas os terrenos visitáveis e suas conexões. Utiliza uma matriz
 * de adjacência (Map de Maps) para armazenar vértices e arestas com seus respectivos
 * pesos/direções. Esta representação é fundamental para o algoritmo A* encontrar
 * o caminho mais curto entre dois pontos.
 */
class Graph {
	// Estrutura de adjacência do grafo: chave=rótulo do vértice, valor=mapa de vizinhos e direções.
	protected Map<String, Map<String, Integer>> vertexes = new HashMap<>();

	/**
	 * Adiciona um novo vértice (nó) ao grafo se não existir.
	 * Cada vértice é representado por um rótulo no formato "x:y" (coordenadas).
	 * Inicializa um HashMap vazio para armazenar os vizinhos deste vértice.
	 *
	 * @param label O rótulo único do vértice no formato "x:y".
	 */
	public void addVertex(String label) {
		this.vertexes.putIfAbsent(label, new HashMap<>());
	}

	/**
	 * Adiciona uma aresta direcionada entre dois vértices.
	 * A aresta armazena o valor de direção (1, 2, 3, 4) que corresponde ao movimento
	 * necessário (cima, direita, baixo, esquerda, respectivamente).
	 *
	 * @param originLabel  O rótulo do vértice de origem.
	 * @param destinyLabel O rótulo do vértice de destino.
	 * @param direction    O valor da direção (1-4) representando o tipo de movimento.
	 */
	public void addEdge(String originLabel, String destinyLabel, int direction) {
		this.vertexes.get(originLabel).put(destinyLabel, direction);
	}

	/**
	 * Retorna todos os vértices vizinhos de um vértice especificado.
	 * Retorna um mapa com os rótulos dos vizinhos como chaves e suas direções como valores.
	 * Utilizado durante a expansão de nós no algoritmo A*.
	 *
	 * @param label O rótulo do vértice central.
	 * @return Um mapa contendo vértices vizinhos e suas direções (1-4).
	 */
	public Map<String, Integer> getNeighbors(String label) {
		return this.vertexes.get(label);
	}

	/**
	 * Converte um rótulo de vértice no formato "x:y" para um array de coordenadas.
	 * Operação inversa de convertCoordinatesToLabel.
	 * Exemplo: "5:10" → [5, 10]
	 *
	 * @param label O rótulo do vértice no formato "x:y".
	 * @return Um array [x, y] contendo as coordenadas correspondentes.
	 */
	public int[] convertLabelToCoordinates(String label) {
		// Divide o rótulo "x:y" em componentes separados.
		String[] coordinates = label.split(":");
		// Converte as strings para inteiros e retorna como array [x, y].
		return new int[]{
				Integer.parseInt(coordinates[0]),
				Integer.parseInt(coordinates[1])
		};
	}

	/**
	 * Converte coordenadas [x, y] para um rótulo de vértice no formato "x:y".
	 * Operação inversa de convertLabelToCoordinates.
	 * Exemplo: [5, 10] → "5:10"
	 *
	 * @param coordinates Um array [x, y] com as coordenadas do vértice.
	 * @return O rótulo único do vértice no formato "x:y".
	 */
	public String convertCoordinatesToLabel(int[] coordinates) {
		// Converte array [x, y] para string "x:y" (formato padrão de rótulo no grafo).
		return Integer.toString(coordinates[0]) + ":" + Integer.toString(coordinates[1]);
	}

	/**
	 * Reconstrói o caminho completo a partir do nó destino até a origem.
	 * Percorre a cadeia de nós pais/raízes até encontrar a origem (nó sem pai),
	 * então inverte a ordem para obter o caminho correto.
	 * Utilizando essa abordagem conseguimos rastrear exatamente como chegamos ao destino.
	 *
	 * @param path         Lista de todos os nós visitados durante a busca A*.
	 * @param destinyLabel O rótulo do nó destino a partir do qual reconstruir.
	 * @return Uma lista de rótulos representando o caminho do origem ao destino.
	 */
	public ArrayList<String> buildPathFromNodes(ArrayList<Node> path, String destinyLabel) {

		// Armazenará o caminho final (do origem ao destino).
		ArrayList<String> reconstructedPath = new ArrayList<>();

		// Busca o nó destino na lista de nós visitados durante A*.
		Node destiny;
		for (Node node : path) {
			if (node.label.equals(destinyLabel)) {
				destiny = node;

				// Percorre a cadeia de pais até encontrar a origem (nó sem pai).
				while (destiny != null) {
					reconstructedPath.add(destiny.label);
					destiny = destiny.root;
				}

				// Encontrou o destino, pode sair do loop.
				break;
			}
		}

		// Inverte a ordem para obter origem → destino (em vez de destino → origem).
		Collections.reverse(reconstructedPath);
		// Retorna o caminho final pronto para navegação.
		return reconstructedPath;
	}

	/**
	 * Implementa o algoritmo de busca A* para encontrar o caminho mais curto.
	 * O A* combina a eficiência da busca gulosa com a otimalidade da busca em profundidade,
	 * usando a heurística de Manhattan Distance para guiar a exploração.
	 * Mantém dois conjuntos: openSet (nós a explorar) e closedSet (nós já explorados).
	 * Quando múltiplos nós têm a mesma distância mínima, escolhe aleatoriamente para
	 * adicionar variedade ao comportamento do Ladrão.
	 *
	 * @param origin  O rótulo do vértice de origem.
	 * @param destiny O rótulo do vértice de destino.
	 * @return Um ArrayList com o caminho completo de origem a destino, ou null se impossível.
	 */
	public ArrayList<String> findPathAStar(String origin, String destiny) {
		// Conjunto aberto: nós candidatos a exploração (inicializa com origem a distância 0).
		Map<String, Integer> openSet = new HashMap<String, Integer>() {{
			put(origin, 0);
		}};

		// Conjunto fechado: nós já explorados (evita revisitar).
		ArrayList<String> closedSet = new ArrayList<>();

		// Árvore de busca: mantém referências de pais para reconstruir caminho.
		ArrayList<Node> path = new ArrayList<>();

		// Validações: se já está no destino ou destino não existe no grafo, retorna null.
		if (origin.equals(destiny) || this.getNeighbors(destiny) == null) {
			return null;
		}

		// Gerador de números aleatórios para desempate quando há múltiplos candidatos.
		Random vertexSelector = new Random();

		// Loop principal: continua enquanto houver nós candidatos a explorar.
		while (!(openSet.isEmpty())) {
			// Encontra a distância mínima entre todos os nós em aberto (guia a busca).
			int minDistance = Collections.min(openSet.entrySet(), Map.Entry.comparingByValue()).getValue();
			// Coleta todos os nós com distância mínima (pode haver múltiplos candidatos).
			ArrayList<String> vertexesWithMinDistance = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : openSet.entrySet()) {
				// Extrai rótulo e distância do nó atual.
				String vertex = entry.getKey();
				int distance = entry.getValue();

				// Se este nó tem a distância mínima, inclui na lista de candidatos.
				if (distance == minDistance) {
					vertexesWithMinDistance.add(vertex);
				}
			}

			// Seleciona aleatoriamente um candidato (adiciona variedade ao comportamento).
			String current = vertexesWithMinDistance.get(vertexSelector.nextInt(vertexesWithMinDistance.size()));

			// Se chegou ao destino, reconstrói e retorna o caminho completo.
			if (current.equals(destiny)) {
				return this.buildPathFromNodes(path, destiny);
			}

			// Move o nó atual do conjunto aberto para análise.
			openSet.remove(current);

			// Registra este nó na árvore de busca (com pai null por enquanto).
			path.add(new Node(current, null));

			// Converte o rótulo do nó atual em coordenadas para calcular distâncias.
			int[] currentCoordinates = this.convertLabelToCoordinates(current);

			// Analisa todos os vizinhos do nó atual para possível exploração.
			for (String neighbor : this.getNeighbors(current).keySet()) {
				// Se vizinho ainda não foi explorado ou está em análise.
				if (!(closedSet.contains(neighbor))) {
					// Se vizinho não está no conjunto aberto, será adicionado.
					if (!(openSet.containsKey(neighbor))) {
						// Obtém coordenadas do vizinho para cálculo de heurística.
						int[] neighborCoordinates = this.convertLabelToCoordinates(neighbor);

						// Encontra o nó pai na árvore e cria nova entrada para o vizinho.
						for (Node node : path) {
							if (node.label == current) {
								path.add(new Node(neighbor, node));
								break;
							}
						}

						// Adiciona vizinho ao conjunto aberto com heurística de Manhattan.
						openSet.put(
								neighbor,
								HScore.calculateManhattanDistance(currentCoordinates, neighborCoordinates));
					}
				}
			}
			// Marca nó atual como permanentemente explorado (não precisa revisitar).
			closedSet.add(current);
		}
		// Se saiu do loop sem encontrar destino, não há solução (caminho bloqueado).
		return null;
	}
}

/**
 * Classe Ladrão representa um agente inteligente que navega em um labirinto
 * perseguindo poupadores para roubar suas moedas.
 * Utiliza algoritmos de busca (A*), memória espacial e heurísticas sensoriais
 * para tomar decisões estratégicas sobre movimento.
 *
 * Capacidades do Ladrão:
 * - Visitação: Campo de visão 5x5 (2 tiles em cada direção).
 * - Olfato: Detecta Poupadores em um raio de 3x3 via cheiro.
 * - Memória: Mantém mapa mental do labirinto explorado (30x30).
 * - Busca: Utiliza A* para planejamento de caminhos.
 * - Estratégia: Alterna entre perseguir (com cooldown) e explorar.
 */
public class Ladrao extends ProgramaLadrao {
	// Matriz 30x30 que armazena o mapa mental do Ladrão (valores: -2=desconhecido, -1=bloqueado, 0=vazio, etc).
	protected int[][] knownField;

	// Grafo dinâmico construído a partir da visão ou memória para planejamento de caminhos via A*.
	protected Graph graph;

	// Rótulo do terreno atual que o Ladrão está tentando alcançar durante exploração.
	private String explorationObjectiveLocation;

	// Mapa de cooldowns para cada Poupador: chave=ID do Poupador, valor=turnos até poder roubar novamente.
	private Map<Integer, Integer> targetRefreshRate;

	// Saldo de moedas do turno anterior (usado para detectar roubo bem-sucedido comparando com getCurrentMoney()).
	private int previousMoneyOnHold;

	// Lista de valores que representam terrenos intransponíveis (usada para validação em isTileBlocked).
	protected ArrayList<Integer> nonVisitableLands = new ArrayList<>(
			Arrays.asList(
					-1,   // Fora de alcance da visão (invisibilidade).
					1,    // Parede (obstáculo físico).
					3,    // Banco (local especial, intransponível).
					4,    // Moeda solta (ocupada, não passável).
					5,    // Pastilha do Poder (especial, não passável).
					200,  // Outro Ladrão ID 200 (colisão com agente).
					210,  // Outro Ladrão ID 210 (colisão com agente).
					220,  // Outro Ladrão ID 220 (colisão com agente).
					230   // Outro Ladrão ID 230 (colisão com agente).
			));

	/**
	 * Inicializa a variável de memória bidimensional que armazena o mapa do labirinto.
	 * Cada célula contém um valor inteiro representando o tipo de terreno:
	 *   -2: Desconhecido (nunca visto)
	 *   -1: Fora de alcance/invisibilidade
	 *    0: Espaço vazio (caminhável)
	 *    1: Parede (intransponível)
	 *    3: Banco (special location)
	 *    4: Moeda (objeto colecionável)
	 *    5: Pastilha do Poder (item especial)
	 *  100, 110: Poupadores (IDs dos agentes)
	 *  200-230: Ladrões (IDs dos agentes)
	 * Este sistema de memória permite que o Ladrão planeje caminhos mesmo
	 * sem visão completa do labirinto.
	 */
	private void initializeMemoryField() {
		// Cria matriz 30x30 vazia.
		this.knownField = new int[30][30];
		// Preenche todas as células com -2 (desconhecido).
		for (int[] field : this.knownField) {
			Arrays.fill(field, -2);
		}
	}

	/**
	 * Construtor público da classe Ladrão.
	 * Inicializa:
	 * - O mapa mental do labirinto (vazio/desconhecido).
	 * - O cooldown de roubo para cada Poupador (evita roubar constantemente).
	 * - O valor anterior de moedas (para detectar quando houve roubo bem-sucedido).
	 *
	 * O cooldown é importante para a mecânica de jogo: após roubar um Poupador,
	 * o Ladrão não pode roubar do mesmo alvo imediatamente.
	 */
	public Ladrao() {
		// Inicializa o mapa mental com todos os terrenos desconhecidos.
		this.initializeMemoryField();
		// Cria cooldown para cada Poupador (100 e 110), iniciados em 0 (pronto para roubar).
		this.targetRefreshRate = new HashMap<Integer, Integer>() {
			{
				put(100, 0); // Poupador com ID 100 - cooldown inicial zero.
				put(110, 0); // Poupador com ID 110 - cooldown inicial zero.
			}
		};
		// Armazena saldo inicial para detectar roubo no primeiro turno.
		this.previousMoneyOnHold = this.getCurrentMoney();
	}

	/**
	 * Pega os terrenos adjacentes visitáveis em relação à visão atual.
	 * Percorre os 4 vizinhos diretos (cima, direita, baixo, esquerda) em ordem,
	 * verificando se são visitáveis dentro dos limites do labirinto.
	 * Ordenação: 1 (cima), 2 (direita), 3 (baixo), 4 (esquerda).
	 * Usado durante a construção do grafo a partir da visão do Ladrão.
	 *
	 * @param x A coordenada "x" (coluna) do terreno central.
	 * @param y A coordenada "y" (linha) do terreno central.
	 * @return Mapa com terrenos adjacentes e suas direções de movimento.
	 */
	private Map<String, Integer> getAdjacentWalkableFromVision(int x, int y) {
		// Deltas de coordenadas para os 4 vizinhos: (0,-1)=cima, (1,0)=direita, (0,1)=baixo, (-1,0)=esquerda.
		int[] adjacentLandsIndex = new int[]{0, -1, 0, 1, 1, 0, -1, 0};

		// Mapa que armazenará vizinhos encontrados: chave=rótulo "x:y", valor=direção 1-4.
		Map<String, Integer> adjacentLands = new HashMap<>();

		// Código de direção do vizinho (1=cima, 2=direita, 3=baixo, 4=esquerda).
		// Aumenta a cada iteração para refletir a mudança de vizinho.
		int landTravelDirection = 1;

		// Processa os 4 pares de deltas (cada um representa um vizinho).
		for (int i = 0; i < adjacentLandsIndex.length; i += 2) {
			// Calcula coordenadas do vizinho adicionando delta ao ponto central.
			int adjacentLandX = adjacentLandsIndex[i] + x;
			int adjacentLandY = adjacentLandsIndex[i + 1] + y;

			// Verifica se vizinho está dentro dos limites do labirinto 30x30.
			if (0 <= adjacentLandX && adjacentLandX <= 29) {
				if (0 <= adjacentLandY && adjacentLandY <= 29) {
					// Se terreno é passável, adiciona ao mapa de adjacência.
					if (!this.isTileBlocked(adjacentLandX, adjacentLandY)) {
						// Converte coordenadas para rótulo e armazena com código de direção.
						adjacentLands.put(
								this.graph.convertCoordinatesToLabel(new int[]{adjacentLandX, adjacentLandY}),
								landTravelDirection);
					}
				}
			}
			// Prepara código de direção para próximo vizinho (1→2→3→4).
			landTravelDirection++;
		}

		// Retorna mapa com todos os vizinhos passáveis e seus códigos de direção.
		return adjacentLands;
	}

	/**
	 * Pega os terrenos adjacentes visitáveis em relação à memória.
	 * Similar ao método de visão, mas trabalha com o mapa memorizado.
	 * Ordenação: 4 (esquerda), 3 (baixo), 2 (direita), 1 (cima).
	 * Esta ordem reversa é importante para a coerência do algoritmo de navegação.
	 * Usado durante a construção do grafo a partir da memória do Ladrão.
	 *
	 * @param x A coordenada "x" (coluna) do terreno central.
	 * @param y A coordenada "y" (linha) do terreno central.
	 * @return Mapa com terrenos adjacentes e suas direções de movimento.
	 */
	private Map<String, Integer> getAdjacentWalkableFromMemory(int x, int y) {
		// Deltas de coordenadas para os 4 vizinhos (mesma estrutura que visão).
		int[] adjacentLandsIndex = new int[]{0, -1, 0, 1, 1, 0, -1, 0};

		// Mapa de vizinhos passáveis identificados na memória.
		Map<String, Integer> adjacentLands = new HashMap<>();

		// Código de direção do vizinho (começa em 4 para ordem reversa: 4,3,2,1).
		// Esta ordem reversa é importante para consistência do algoritmo de navegação.
		int landTravelDirection = 4;

		// Processa os 4 pares de deltas (cada um representa um vizinho).
		for (int i = 0; i < adjacentLandsIndex.length; i += 2) {
			// Calcula coordenadas do vizinho adicionando delta ao ponto central.
			int adjacentLandX = adjacentLandsIndex[i] + x;
			int adjacentLandY = adjacentLandsIndex[i + 1] + y;

			// Verifica se vizinho está dentro dos limites do labirinto 30x30.
			if (0 <= adjacentLandX && adjacentLandX <= 29) {
				if (0 <= adjacentLandY && adjacentLandY <= 29) {
					// Se terreno na memória é passável, adiciona ao mapa de adjacência.
					if (!this.isTileBlocked(adjacentLandX, adjacentLandY)) {
						// Converte coordenadas para rótulo e armazena com código de direção (reverso).
						adjacentLands.put(
								this.graph.convertCoordinatesToLabel(new int[]{adjacentLandX, adjacentLandY}),
								landTravelDirection);
					}
				}
			}
			// Decrementa código de direção para próximo vizinho (4→3→2→1).
			landTravelDirection--;
		}

		// Retorna mapa com vizinhos passáveis e seus códigos de direção (ordem reversa).
		return adjacentLands;
	}

	/**
	 * Constrói um grafo completo baseado na memória do Ladrão.
	 * Percorre toda a matriz de conhecimento (30x30) e conecta todos os terrenos
	 * visitáveis. Este grafo representa o "mapa mental" completo que o Ladrão tem
	 * do labirinto. É utilizado durante a exploração para planejar rotas de longo prazo.
	 * O grafo é criado do zero a cada invocação para refletir mudanças na memória.
	 */
	private void buildGraphFromMemory() {
		// Descarta grafo anterior e cria estrutura vazia.
		this.graph = new Graph();

		// Itera sobre todas as 900 células da matriz 30x30.
		for (int y = 0; y <= this.knownField.length - 1; y++) {
			for (int x = 0; x <= this.knownField[y].length - 1; x++) {
				// Se célula é passável (não está bloqueada), a inclui no grafo.
				if (!this.isTileBlocked(y, x)) {
					// Cria vértice para este terreno no grafo.
					String currentLand = this.graph.convertCoordinatesToLabel(new int[]{y, x});
					this.graph.addVertex(currentLand);

					// Obtém todos os vizinhos passáveis deste terreno.
					for (Map.Entry<String, Integer> entry : this.getAdjacentWalkableFromMemory(y, x).entrySet()) {
						// Extrai informações do vizinho: rótulo e direção.
						String adjVertex = entry.getKey();
						int adjVertexDirection = entry.getValue();

						// Garante que vizinho também existe como vértice no grafo.
						this.graph.addVertex(adjVertex);

						// Cria aresta direcionada: terreno atual → vizinho com código de direção.
						this.graph.addEdge(currentLand, adjVertex, adjVertexDirection);
					}
				}
			}
		}
	}

	/**
	 * Constrói um grafo local baseado apenas na visão atual do Ladrão.
	 * Percorre a área visível (5x5 ao redor da posição do Ladrão) e conecta apenas
	 * os terrenos que podem ser vistos no momento. É utilizado durante a perseguição
	 * de poupadores para navegação em tempo real.
	 * Este grafo tem escopo limitado e é mais rápido de construir que o grafo da memória.
	 */
	private void buildGraphFromVision() {
		// Descarta grafo anterior e cria estrutura vazia.
		this.graph = new Graph();

		// Obtém posição atual para calcular raio de visão (5x5 grid).
		int[] positions = this.getCurrentPosition();
		int thiefX = positions[0];
		int thiefY = positions[1];

		// Itera sobre a área 5x5 visível ao redor da posição do Ladrão.
		for (int y = thiefY - 2; y <= thiefY + 2; y++) {
			for (int x = thiefX - 2; x <= thiefX + 2; x++) {
				// Valida se ponto está dentro dos limites do labirinto 30x30.
				if (0 <= x && x <= 29) {
					if (0 <= y && y <= 29) {
						// Se célula visível é passável, a inclui no grafo local.
						if (!this.isTileBlocked(y, x)) {
							// Cria vértice para este terreno visível.
							String currentLand = this.graph.convertCoordinatesToLabel(new int[]{x, y});
							this.graph.addVertex(currentLand);

							// Obtém vizinhos passáveis deste terreno (também na visão).
							for (Map.Entry<String, Integer> entry : this.getAdjacentWalkableFromVision(x, y).entrySet()) {
								// Extrai informações do vizinho: rótulo e direção.
								String adjVertex = entry.getKey();
								int adjVertexDirection = entry.getValue();

								// Garante que vizinho também existe como vértice.
								this.graph.addVertex(adjVertex);

								// Cria aresta: terreno atual → vizinho com direção.
								this.graph.addEdge(currentLand, adjVertex, adjVertexDirection);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Verifica se há evidencias de um Poupador perto através do olfato.
	 * O Ladrão detecta Poupadores pelo seu "cheiro" em um raio de 3x3.
	 * Se qualquer valor do vetor de olfato for diferente de 0, significa que
	 * um Poupador passou por ali recentemente e ainda deixa traços.
	 * Este mecanismo permite ao Ladrão rastrear Poupadores mesmo sem vê-los diretamente.
	 *
	 * @return true se há cheiro de Poupador detectado, false caso contrário.
	 */
	protected boolean isTargetDetectedBySmell() {
		// Se qualquer posição do sensor 3x3 tem cheiro (≠0), Poupador foi detectado.
		return Arrays.stream(this.getSmellSensor()).anyMatch(i -> i != 0);
	}

	/**
	 * Verifica se há um Poupador na visão direta do Ladrão.
	 * Procura pelos IDs dos Poupadores (100 ou 110) no array de visão.
	 * Se encontrado, o Poupador pode ser perseguido diretamente (se não estiver em cooldown).
	 * Este é o método mais confiável de detecção do Poupador.
	 *
	 * @return true se um Poupador é visível, false caso contrário.
	 */
	protected boolean isTargetVisible() {
		// Procura pelos IDs de Poupadores (100 ou 110) no array de visão 5x5.
		return Arrays.stream(this.getCurrentVision()).anyMatch(i -> i == 100 || i == 110);
	}

	/**
	 * Decide a próxima ação do Ladrão baseando-se em sua situação.
	 * Prioridade de decisão:
	 * 1. Se o Poupador é visível E não está em cooldown: PERSEGUIR
	 * 2. Se o cheiro do Poupador é detectado: RASTREAR (seguir o cheiro mais forte)
	 * 3. Senão: EXPLORAR (buscar novos territórios)
	 * Este método é o córao da IA do Ladrão, orquestrando seu comportamento.
	 *
	 * @return O código de direção do movimento a executar (1-4 ou 0 para parado).
	 */
	protected int decideNextActionTarget() {
		// Obtém coordenadas atuais do Ladrão para análise de vizinhança.
		int[] positions = this.getCurrentPosition();
		int thiefX = positions[0];
		int thiefY = positions[1];

		// PRIORIDADE 1: Se Poupador é visível, tenta persegui-lo (se não há cooldown).
		if (this.isTargetVisible()) {
			// Procura em toda a área 5x5 visível pelo Poupador sem cooldown.
			for (int y = thiefY - 2; y <= thiefY + 2; y++) {
				for (int x = thiefX - 2; x <= thiefX + 2; x++) {
					// Valida coordenadas dentro do labirinto.
					if (0 <= x && x <= 29) {
						if (0 <= y && y <= 29) {
							// Se há Poupador 100 e cooldown expirou (== 0), persegue-o.
							if (this.knownField[y][x] == 100 && this.targetRefreshRate.get(100) == 0) {
								// Inicia perseguição e retorna movimento.
								return this.chaseTarget(new int[]{x, y});
							}
							// Se há Poupador 110 e cooldown expirou (== 0), persegue-o.
							else if (this.knownField[y][x] == 110 && this.targetRefreshRate.get(110) == 0) {
								// Inicia perseguição e retorna movimento.
								return this.chaseTarget(new int[]{x, y});
							}
						}
					}
				}
			}
		}
		// PRIORIDADE 2: Se não vê Poupador, tenta rastrear pelo olfato.
		else if (this.isTargetDetectedBySmell()) {
			// Obtém array 3x3 com intensidades de cheiro detectadas.
			int[] saverSmell = this.getSmellSensor();
			// Índice atual ao iterar sobre o array de cheiro.
			int saverSmellIndex = 0;
			// Melhor cheiro detectado (menor valor indica mais próximo).
			int minSaverSmell = Integer.MAX_VALUE;
			// Coordenadas da posição com melhor cheiro.
			int[] minSmellPosition = null;

			// Procura sobre a área 3x3 de olfato ao redor da posição.
			for (int y = thiefY - 1; y <= thiefY + 1; y++) {
				for (int x = thiefX - 1; x <= thiefX + 1; x++) {
					// Pula a posição central (Ladrão não cheira a si mesmo).
					if (!(x == thiefX && y == thiefY)) {
						// Valida coordenadas dentro do labirinto.
						if (0 <= x && x <= 29) {
							if (0 <= y && y <= 29) {
								// Se cheiro é melhor (menor/mais forte) e válido (> 0).
								if (saverSmell[saverSmellIndex] <= minSaverSmell
										&& !(saverSmell[saverSmellIndex] <= 0)) {
									// Atualiza melhor cheiro encontrado nesta iteração.
									minSaverSmell = saverSmell[saverSmellIndex];
									// Atualiza coordenadas da melhor posição.
									minSmellPosition = new int[]{x, y};
								}
							}
						}
						// Avança índice para próxima célula do array de olfato.
						saverSmellIndex++;
					}
				}
			}

			// Se encontrou cheiro, persegue direção; senão explora.
			return minSmellPosition != null ? this.chaseTarget(minSmellPosition) : this.exploreEnvironment();
		}
		// PRIORIDADE 3: Sem visão nem olfato, explora o labirinto.
		return this.exploreEnvironment();
	}

	/**
	 * Verifica se um terreno é intransponível (bloqueado).
	 * Um terreno está bloqueado se está na lista de terrenos não visitáveis:
	 * paredes, bancos, moedas, pastilhas do poder, ou outros Ladrões.
	 * Consulta o valor na matriz de memória do Ladrão.
	 *
	 * @param x A coordenada "x" do terreno.
	 * @param y A coordenada "y" do terreno.
	 * @return true se o terreno é bloqueado/intransponível, false se passável.
	 */
	protected boolean isTileBlocked(int x, int y) {
		return nonVisitableLands.contains(this.knownField[x][y]);
	}

	/**
	 * Verifica se uma posição específica do mapa contém um terreno desconhecido.
	 * Terrenos desconhecidos (valor -2) representam áreas do labirinto que o Ladrão
	 * ainda não explorou. Esta verificação é crucial para a estratégia de exploração,
	 * permitindo que o Ladrão identifique territórios novos a serem mapeados.
	 * Consulta o valor armazenado na matriz de memória knownField.
	 *
	 * @param x A coordenada "x" (coluna) do terreno a verificar.
	 * @param y A coordenada "y" (linha) do terreno a verificar.
	 * @return true se o terreno possui valor -2 (desconhecido), false caso contrário.
	 */
	protected boolean isTileUnknown(int x, int y) {
		return this.knownField[x][y] == -2;
	}

	/**
	 * Verifica se uma posição específica do mapa contém um terreno conhecido e caminável.
	 * Terrenos conhecidos (valor 0) representam áreas que o Ladrão já explorou e sabe
	 * que são passáveis (não contêm obstáculos). Esta verificação é fundamental para
	 * construir objetivos de exploração seguros durante a navegação pelo labirinto.
	 * Consulta o valor armazenado na matriz de memória knownField.
	 *
	 * @param x A coordenada "x" (coluna) do terreno a verificar.
	 * @param y A coordenada "y" (linha) do terreno a verificar.
	 * @return true se o terreno possui valor 0 (conhecido/caminável), false caso contrário.
	 */
	protected boolean isTileKnown(int x, int y) {
		return this.knownField[x][y] == 0;
	}

	/**
	 * Verifica se o Ladrão já chegou ao seu objetivo de exploração atual.
	 * Compara a posição atual do Ladrão com o local armazenado em explorationObjectiveLocation.
	 * Quando este método retorna true, o Ladrão deve selecionar um novo objetivo para
	 * continuar explorando novos territórios. Esta verificação evita "ciclos" onde o Ladrão
	 * fica preso tentando alcançar um objetivo já atingido.
	 *
	 * @return true se a posição atual do Ladrão é idêntica ao objetivo de exploração,
	 *         false caso ainda esteja viajando em direção ao objetivo.
	 */
	protected boolean hasReachedExplorationGoal() {
		return this.graph.convertCoordinatesToLabel(this.getCurrentPosition()).equals(this.explorationObjectiveLocation);
	}

	/**
	 * Retorna a visão atual do Ladrão como um array de inteiros.
	 * A visão é uma matriz 5x5 (25 elementos) representando o que o Ladrão consegue ver
	 * em seu campo de visão: 2 tiles em cada direção (cima, baixo, esquerda, direita).
	 * Cada elemento do array contém um código inteiro representando o tipo de terreno
	 * ou agente naquela posição (ex: 100=Poupador, 1=Parede, 0=Vazio, -1=Fora do alcance).
	 * Consulta o sensor integrado: sensor.getVisaoIdentificacao().
	 *
	 * @return Array de 25 inteiros representando a visão 5x5 do Ladrão.
	 */
	protected int[] getCurrentVision() {
		return this.sensor.getVisaoIdentificacao();
	}

	/**
	 * Retorna a quantidade total de moedas roubadas pelo Ladrão até o momento.
	 * Este valor é fundamental para detectar quando um roubo foi bem-sucedido
	 * (comparando com previousMoneyOnHold). A diferença entre leituras consecutivas
	 * indica quantas moedas foram roubadas neste turno, ativando o cooldown de roubo.
	 * Consulta o sensor integrado: sensor.getNumeroDeMoedas().
	 *
	 * @return Um inteiro representando o saldo total de moedas que o Ladrão carrega.
	 */
	protected int getCurrentMoney() {
		return this.sensor.getNumeroDeMoedas();
	}

	/**
	 * Retorna o sensor de olfato do Ladrão como um array de inteiros.
	 * O olfato é uma matriz 3x3 (9 elementos) que detecta o "cheiro" deixado por Poupadores.
	 * Cada elemento contém um valor inteiro representando a intensidade do cheiro naquela posição:
	 * - 0: Sem cheiro detectado
	 * - > 0: Presença de cheiro (quanto maior, mais recente ou próximo)
	 * - < 0: Sem informação ou fora do alcance
	 * Este sensor permite ao Ladrão rastrear Poupadores mesmo sem vê-los diretamente.
	 * Consulta o sensor integrado: sensor.getAmbienteOlfatoPoupador().
	 *
	 * @return Array de 9 inteiros representando a intensidade de cheiro em cada posição 3x3.
	 */
	protected int[] getSmellSensor() {
		return this.sensor.getAmbienteOlfatoPoupador();
	}

	/**
	 * Retorna a posição atual do Ladrão no labirinto como um array [x, y].
	 * As coordenadas correspondem à célula específica do grid 30x30 onde o Ladrão está localizado.
	 * Este método é essencial para:
	 * - Determinar o raio de visão e olfato (5x5 e 3x3 respectivamente)
	 * - Construir grafos com base na visão/memória ao redor da posição
	 * - Calcular distâncias de Manhattan para o A*
	 * - Verificar se o Ladrão chegou a um objetivo
	 * Consulta o sensor integrado e converte java.awt.Point para array int[2].
	 *
	 * @return Array [x, y] contendo as coordenadas atuais do Ladrão no labirinto.
	 */
	protected int[] getCurrentPosition() {
		// Obtém a posição atual do Ladrão diretamente do sensor do simulador.
		java.awt.Point currentPosition = this.sensor.getPosicao();
		// Extrai a coordenada X e converte para tipo inteiro (double → int).
		int x = (int) currentPosition.getX();
		// Extrai a coordenada Y e converte para tipo inteiro (double → int).
		int y = (int) currentPosition.getY();
		// Retorna um array contendo ambas as coordenadas [x, y] do Ladrão no grid 30x30.
		return new int[]{x, y};
	}

	/**
	 * Decrementa o cooldown de roubo para todos os Poupadores a cada turno.
	 * O cooldown impede que o Ladrão roube repetidamente do mesmo Poupador em turno consecutivos.
	 * Cada Poupador (100 e 110) tem seu próprio contador:
	 * - Quando counter > 0: Roubo ainda está em cooldown, não é possível roubar
	 * - Quando counter = 0: Cooldown expirou, roubo é possível novamente
	 * Este mecanismo adiciona realismo e força o Ladrão a variar seus objetivos.
	 * É chamado uma vez por turno no método acao().
	 */
	private void updateTargetCooldown() {
		// Itera sobre cada Poupador rastreado no mapa de cooldowns.
		for (Integer saverId : this.targetRefreshRate.keySet()) {
			// Se o contador de cooldown ainda está ativo (maior que zero).
			if (this.targetRefreshRate.get(saverId) > 0) {
				// Decrementa o cooldown em 1 turno (aproxima do tempo de roubo permitido).
				this.targetRefreshRate.put(saverId, this.targetRefreshRate.get(saverId) - 1);
			}
		}
	}

	/**
	 * Atualiza a memória do Ladrão com base em sua visão atual.
	 * Sincroniza o que o Ladrão vê com seu mapa mental, "aprendendo" os terrenos
	 * e marcando-os como conhecidos. Evita que o Ladrão "esqueça" territórios
	 * já explorados quando paredes bloqueiam sua visão.
	 * Este é um processo fundamental de acumulação de conhecimento do labirinto.
	 */
	private void updateMemoryWithVision() {
		// Obtém as coordenadas atuais da posição do Ladrão no labirinto.
		int[] positions = this.getCurrentPosition();
		int thiefX = positions[0];
		int thiefY = positions[1];

		// Captura o array 5x5 com a visão atual do Ladrão (25 elementos).
		int[] currentView = this.getCurrentVision();

		// Índice para rastreamento na iteração do array de visão (começando em 0).
		int gridViewIndex = 0;

		// Itera sobre todas as 25 posições da matriz de visão 5x5 (de -2 a +2 em cada eixo).
		for (int y = thiefY - 2; y <= thiefY + 2; y++) {
			for (int x = thiefX - 2; x <= thiefX + 2; x++) {
				// Ignora a célula central que contém a posição do próprio Ladrão.
				if (!(x == thiefX && y == thiefY)) {
					// Verifica se as coordenadas do terreno estão dentro dos
					// limites válidos do labirinto (grid 30x30).
					if (0 <= x && x <= 29) {
						if (0 <= y && y <= 29) {
							// Lógica de persistência de memória: evita que o Ladrão esqueça
							// terrenos já conhecidos quando paredes bloqueiam a visão (retornam -2).
							// Atualiza apenas se: (1) visão revela novo terreno, ou (2) ambos não são -2.
							// Isso preserva conhecimento mesmo com obstáculos temporários bloqueando a visão.
							if ((currentView[gridViewIndex] != -2 && this.knownField[y][x] == -2) ||
									(currentView[gridViewIndex] != -2 && this.knownField[y][x] != -2)) {
								// Sincroniza o valor do terreno com o que é visível atualmente.
								this.knownField[y][x] = currentView[gridViewIndex];
							}
						}
					}
					// Incrementa o índice para processar o próximo elemento do array de visão.
					gridViewIndex++;
				} else {
					// Define a célula do Ladrão como terreno vazio (0) pois ele está ali.
					// Garante que o próprio Ladrão nunca marca sua posição como desconhecida ou bloqueada.
					this.knownField[y][x] = 0;
				}
			}
		}
	}

	/**
	 * Persegue um Poupador alvo de forma estratégica.
	 * Constrói um grafo local baseado na visão, executa A* até o alvo,
	 * e implementa um sistema de cooldown para evitar roubo imediato repetido.
	 * Se conseguir roubar moedas (detectado por mudança no saldo), ativa
	 * um cooldown que impede roubo imediato do mesmo alvo.
	 * Este método é crucial para a estratégia ofensiva do Ladrão.
	 *
	 * @param targetLocation O array [x, y] com a posição do Poupador alvo.
	 * @return O código de direção do próximo movimento (1-4).
	 */
	private int chaseTarget(int[] targetLocation) {
		// Constrói um grafo local imediato baseado apenas na área visível 5x5 (perseguição em tempo real).
		this.buildGraphFromVision();

		// Detecta sucesso de roubo comparando o saldo anterior com o saldo atual do Ladrão.
		if (this.previousMoneyOnHold != this.getCurrentMoney()) {
			// Calcula um limite máximo de cooldown aleatório entre 100 e 150 turnos.
			int maxRefreshRate = (int) Math.random() * (150 - 100) + 100;
			// Calcula a penalidade inicial multiplicando moedas roubadas por 10.
			int stoleCoins = this.getCurrentMoney() * 10;

			// Se não conseguiu moedas (saldo = 0), usa uma penalidade fixa aleatória entre 10-30 turnos.
			if (stoleCoins == 0) {
				stoleCoins = (int) Math.random() * (10 - 30) + 10;
			}

			// Aplica o cooldown ao Poupador roubado (usa o maior entre penalidade e limite máximo).
			// Extrai o ID do Poupador (100 ou 110) da matriz de memória naquela posição.
			this.targetRefreshRate.put(
					this.knownField[targetLocation[1]][targetLocation[0]],
					stoleCoins > maxRefreshRate ? maxRefreshRate : stoleCoins
			);

			// Atualiza a referéncia do saldo anterior para a próxima comparação de roubo bem-sucedido.
			this.previousMoneyOnHold = this.getCurrentMoney();
		}

		// Executa um único passo do algoritmo A* em direção ao Poupador alvo.
		// Retorna o código de direção (1-4) do próximo movimento a executar.
		return this.moveUsingAStar(
				this.graph.convertCoordinatesToLabel(this.getCurrentPosition()),
				this.graph.convertCoordinatesToLabel(targetLocation)
		);
	}

	/**
	 * Encontra o terreno conhecido mais distante da posição atual do Ladrão.
	 * Usa distância de Manhattan para calcular a "longevidade" de cada terreno.
	 * Prioriza terrenos desconhecidos sobre conhecidos para exploração.
	 * Se não houver terreno conhecido, retorna "8:8" (possição do banco como fallback).
	 * Este método suporta a busca exaustiva do labirinto.
	 *
	 * @return O rótulo do vértice mais distante known ou "8:8" como fallback.
	 */
	protected String getFarthestKnownNode() {
		// Mapa temporário que armazena todos os terrenos conhecidos e suas distâncias de Manhattan.
		Map<String, Integer> knownVerticesDistances = new HashMap<>();

		// Gerador de aleatoriedade para selecionar terreno quando múltiplos candidatos existem.
		Random landSelector = new Random();

		// Obtém a posição atual do Ladrão e inverte as coordenadas (x, y) → (y, x) por consistência.
		int[] currentThiefPosition = this.getCurrentPosition();
		currentThiefPosition = new int[]{currentThiefPosition[1], currentThiefPosition[0]};

		// Itera sobre TODOS os vértices do grafo de memória global (não apenas a visão).
		for (String vertex : this.graph.vertexes.keySet()) {
			// Extrai as coordenadas [x, y] do rótulo do vértice no formato "x:y".
			int[] vertexCoordinates = this.graph.convertLabelToCoordinates(vertex);
			// Verifica se este vértice é um terreno conhecido (valor 0 na matriz de memória).
			if (this.isTileKnown(vertexCoordinates[0], vertexCoordinates[1])) {
				// Calcula distância de Manhattan e armazena no mapa de candidatos.
				knownVerticesDistances.put(vertex,
						HScore.calculateManhattanDistance(currentThiefPosition, vertexCoordinates));
			}
		}

		// Coleta todos os rótulos de vértices conhecidos em uma lista manipulável.
		ArrayList<String> vertexes = new ArrayList<>(knownVerticesDistances.keySet());
		// Se há terrenos conhecidos disponíveis, retorna um aleatório.
		if (!vertexes.isEmpty()) {
			// Seleciona e retorna um dos terrenos conhecidos de forma aleatória para variação comportamental.
			return vertexes.get(landSelector.nextInt(vertexes.size()));
		}
		// Se não há list, mas há no mapa, retorna o terreno conhecido mais distante (técnica de desempate).
		else if (!knownVerticesDistances.isEmpty()) {
			// Encontra a entrada com a maior distância e retorna seu rótulo.
			return Collections.max(knownVerticesDistances.entrySet(), Map.Entry.comparingByValue()).getKey();
		}
		// Se não há terrenos conhecidos, retorna a posição do banco (8:8) como fallback seguro.
		else {
			return "8:8";
		}
	}

	/**
	 * Encontra o terreno desconhecido mais distante da posição atual.
	 * Utiliza uma heurística: seleciona terrenos desconhecidos cuja distância
	 * está no "top 50%" (maxDistance/2). Se múltiplos candidatos existem,
	 * escolhe aleatoriamente para adicionar variedade na exploração.
	 * Se não houver desconhecido, cai de volta para getFarthestKnownNode.
	 * Este método garante que o Ladrão sempre tem um objetivo de exploração.
	 *
	 * @return O rótulo do vértice mais distante unknown ou um node known como fallback.
	 */
	protected String getFarthestUnknownNode() {
		// Mapa temporário que armazena todos os terrenos desconhecidos e suas distâncias de Manhattan.
		Map<String, Integer> unknownVerticesDistances = new HashMap<>();

		// Gerador de aleatoriedade para selecionar terreno quando há múltiplos candidatos.
		Random landSelector = new Random();

		// Obtém a posição atual do Ladrão para cálculo de distâncias relativas.
		int[] currentThiefPosition = this.getCurrentPosition();

		// Itera sobre TODOS os vértices do grafo de memória global.
		for (String vertex : this.graph.vertexes.keySet()) {
			// Extrai as coordenadas [x, y] do rótulo do vértice no formato "x:y".
			int[] vertexCoordinates = this.graph.convertLabelToCoordinates(vertex);
			// Verifica se este vértice é um terreno desconhecido (valor -2 na matriz de memória).
			if (this.isTileUnknown(vertexCoordinates[0], vertexCoordinates[1])) {
				// Calcula distância de Manhattan e armazena como candidato de exploração.
				unknownVerticesDistances.put(
						vertex,
						HScore.calculateManhattanDistance(currentThiefPosition, vertexCoordinates));
			}
		}
		// Se há terrenos desconhecidos disponíveis para explorar.
		if (!unknownVerticesDistances.isEmpty()) {
			// Calcula o valor de distância limiar: 50% da máxima distância encontrada.
			// Isso cria um "top 50%" de candidatos para explorar terrenos distantes.
			int maxDistance = (int) Math
					.floor(Collections.max(unknownVerticesDistances.entrySet(), Map.Entry.comparingByValue())
							.getValue() / 2);
			// Lista dos candidatos que estão no "top 50%" de distância (mais distantes).
			ArrayList<String> unknownVerticesMaxDistance = new ArrayList<>();
			// Itera sobre todos os terrenos desconhecidos encontrados.
			for (Map.Entry<String, Integer> entry : unknownVerticesDistances.entrySet()) {
				// Rótulo do vértice sendo analisado.
				String vertex = entry.getKey();
				// Distância de Manhattan deste vértice até a posição atual.
				int distance = entry.getValue();

				// Se a distância está dentro do "top 50%" (acima do limiar calculado).
				if (distance >= maxDistance) {
					// Adiciona este vértice à lista de candidatos prioritários.
					unknownVerticesMaxDistance.add(vertex);
				}
			}

			// Se há múltiplos candidatos no "top 50%", seleciona um aleatoriamente.
			if (unknownVerticesMaxDistance.size() > 1) {
				// Escolhe aleatório dentre os terrenos mais distantes para variação comportamental.
				return unknownVerticesMaxDistance.get(landSelector.nextInt(unknownVerticesMaxDistance.size()));
			}
		}
		// Se não há terrenos desconhecidos, cai de volta para buscar um terreno conhecido.
		// Isso garante que o Ladrão sempre tem um objetivo de navegação.
		return this.getFarthestKnownNode();
	}

	/**
	 * Explora sistematicamente o labirinto em busca de Poupadores.
	 * Constrói um grafo baseado na memória acumulada, define um objetivo de exploração
	 * (preferencialmente um terreno desconhecido), e executa A* até ele.
	 * Quando atinge o objetivo, automaticamente seleciona um novo alvo para evitar
	 * comportamento cíclico. Este método garante que o Ladrão mapeie completamente
	 * o labirinto eventualmente, descobrindo mais Poupadores.
	 *
	 * @return O código de direção do próximo movimento (1-4).
	 */
	private int exploreEnvironment() {
		// Constrói um novo grafo global baseado em TODA a memória acumulada do Labirinto (30x30).
		// Diferente de buildGraphFromVision(), este grafo cobre o mapa completo armazenado.
		this.buildGraphFromMemory();
		// Verifica se o objetivo de exploração ainda é válido ou já foi alcançado.
		if (this.explorationObjectiveLocation == null || this.hasReachedExplorationGoal()) {
			// Seleciona um novo terreno desconhecido distante como próximo objetivo de exploração.
			// O método getFarthestUnknownNode() usará o grafo de memória recentemente construído.
			this.explorationObjectiveLocation = this.getFarthestUnknownNode();
		}
		// Obtém a posição atual do Ladrão para calcular o caminho até o objetivo de exploração.
		int[] thiefPosition = this.getCurrentPosition();
		// Executa um passo do A* em direção ao objetivo de exploração.
		// Retorna o código de direção (1-4) do movimento a executar neste turno.
		return this.moveUsingAStar(
				this.graph.convertCoordinatesToLabel(new int[]{thiefPosition[1], thiefPosition[0]}),
				this.explorationObjectiveLocation);
	}

	/**
	 * Executa uma única iteração do algoritmo A* e retorna o primeiro movimento.
	 * Este método é o "passo" individual do Ladrão: ele planeja um caminho e executa
	 * apenas um movimento desse caminho. No próximo turno, pode replanejar se necessário.
	 * Se o caminho falhar (ex: obstculo novo), seleciona um novo objetivo.
	 *
	 * @param origin  O rótulo da posição de origem.
	 * @param destiny O rótulo da posição de destino desejada.
	 * @return O código de direção do movimento (1-4) ou aleatoriedade como fallback.
	 */
	private int moveUsingAStar(String origin, String destiny) {
		// Executa o algoritmo A* para encontrar o caminho completo entre origem e destino.
		// Se não houver caminho, retorna null (bloqueado por obstáculos).
		ArrayList<String> path = this.graph.findPathAStar(origin, destiny);
		// Verifica se o A* conseguiu encontrar um caminho válido até o destino.
		if (path != null) {
			// Extrai o próximo vértice do caminho planejado (primeiro passo).
			// Busca o código de direção (1-4) que conecta a origem ao próximo vértice.
			// Retorna este código para execução imediata neste turno.
			return this.graph.getNeighbors(path.get(0)).get(path.get(1));
		} else {
			// Se não há caminho, seleciona um novo objetivo desconhecido para tentar explorar.
			this.explorationObjectiveLocation = this.getFarthestUnknownNode();
		}
		// Se o objetivo desconhecido também não for alcançável, seleciona um terreno conhecido como fallback.
		this.explorationObjectiveLocation = this.getFarthestKnownNode();
		// Retorna um movimento aleatório (0-4) como fallback quando não consegue planejar caminho.
		// Valor 0 significa parado, 1-4 são direções válidas.
		return (int) Math.random() * 5;
	}

	/**
	 * Método principal de ação executado a cada turno do jogo.
	 * Orquestra o comportamento completo do Ladrão:
	 * 1. Atualiza a memória com a visão atual.
	 * 2. Decrementa os cooldowns de roubo.
	 * 3. Decide e executa a ação mais apropriada (perseguir ou explorar).
	 *
	 * Esta é a interface entre o loop de simulação e a lógica de IA do Ladrão.
	 * Chamado uma vez por turno de simulação.
	 *
	 * @return O código de direção da ação a executar (1-4 ou 0 para parado).
	 */
	@Override
	public int acao() {
		// Sincroniza a visão atual com a matriz de memória, "aprendendo" novos terrenos explorados.
		// Este passo é essencial para atualizar o conhecimento do Ladrão sobre o labirinto.
		this.updateMemoryWithVision();
		// Decrementa os contadores de cooldown de roubo para cada Poupador (se ainda ativos).
		// Quando um cooldown chega a zero, o Ladrão volta a poder roubar aquele Poupador.
		this.updateTargetCooldown();
		// Avalia a situação atual e executa a melhor ação: perseguir, rastrear ou explorar.
		// Este método é o "coração da IA" que coordena todo o comportamento do Ladrão.
		// Retorna o código de direção (1-4) ou 0 se parado.
		return this.decideNextActionTarget();
	}
}