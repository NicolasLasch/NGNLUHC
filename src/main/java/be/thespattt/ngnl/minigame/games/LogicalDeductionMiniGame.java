package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// Working
// TODO : translate to french
public class LogicalDeductionMiniGame extends MiniGameBase implements Listener {

    private enum GamePhase {
        SETUP,
        COUNTDOWN,
        PLAYING,
        ENDED
    }

    private static class Question {
        final String category;
        final String difficulty;
        final String questionText;
        final String correctAnswer;
        final List<String> incorrectAnswers;

        Question(String category, String difficulty, String questionText, String correctAnswer, List<String> incorrectAnswers) {
            this.category = category;
            this.difficulty = difficulty;
            this.questionText = questionText;
            this.correctAnswer = correctAnswer;
            this.incorrectAnswers = incorrectAnswers;
        }
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private int currentQuestionNumber = 0;
    private final int MAX_QUESTIONS = 10;
    private int questionTimeRemaining = 30;
    private BukkitTask questionTimerTask;
    private BukkitTask gameTimerTask;

    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final Map<UUID, Integer> playerCorrectAnswers = new HashMap<>();
    private final Map<UUID, Long> playerResponseTimes = new HashMap<>();

    private Location arenaCenter;
    private boolean arenaReady = false;
    private Question currentQuestion;
    private boolean questionActive = false;
    private long questionStartTime;

    private final int GAME_DURATION = 6000;
    private final int QUESTION_TIME = 20;

    private final Map<UUID, Boolean> playerHasAnswered = new HashMap<>();
    private List<String> allAnswers = new ArrayList<>();

    private boolean useFrench = false;
    public LogicalDeductionMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.LOGICAL_DEDUCTION, player1WonPvP);

        playerScores.put(player1UUID, 0);
        playerScores.put(player2UUID, 0);
        playerCorrectAnswers.put(player1UUID, 0);
        playerCorrectAnswers.put(player2UUID, 0);
        playerHasAnswered.put(player1UUID, false);
        playerHasAnswered.put(player2UUID, false);
    }


    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupArenaAndStart();
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, 20);
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;
        arenaCenter = new Location(world, x, y, z);
    }

    private void loadChunksSync(World world, Location center, int radius) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunk.load(true);
            }
        }
    }

    private void completeArenaSetupAndStart() {
        buildArena();
        teleportPlayers();
        arenaReady = true;

        MessageUtil.sendMessage(getPlayer1(), "&aArena ready! Starting Logical Deduction...");
        MessageUtil.sendMessage(getPlayer2(), "&aArena ready! Starting Logical Deduction...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildKnowledgeArena(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildKnowledgeArena(World world) {
        buildCircularFloor(world);
        buildLibraryDecorations(world);
        buildArenaBarriers(world);
    }

    private void buildCircularFloor(World world) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= 10) {
                    Material floorMaterial = getFloorMaterial(distance, x, z);
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY(), arenaCenter.getBlockZ() + z).setType(floorMaterial);

                    buildFoundation(world, x, z);
                }
            }
        }
    }

    private Material getFloorMaterial(double distance, int x, int z) {
        if (distance <= 3) {
            return Material.GOLD_BLOCK;
        } else if ((x + z) % 2 == 0) {
            return Material.PURPLE_CONCRETE;
        } else {
            return Material.BLACK_CONCRETE;
        }
    }

    private void buildFoundation(World world, int x, int z) {
        for (int y = -5; y <= -1; y++) {
            world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
        }
    }

    private void buildLibraryDecorations(World world) {
        buildBookshelves(world);
        buildCentralLectern(world);
    }

    private void buildBookshelves(World world) {
        int[] positions = {-8, 8};
        for (int x : positions) {
            for (int z : positions) {
                buildBookshelfPillar(world, x, z);
            }
        }
    }

    private void buildBookshelfPillar(World world, int x, int z) {
        for (int y = 1; y <= 3; y++) {
            world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BOOKSHELF);
        }
        world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + 4, arenaCenter.getBlockZ() + z).setType(Material.GLOWSTONE);
    }

    private void buildCentralLectern(World world) {
        world.getBlockAt(arenaCenter.getBlockX(), arenaCenter.getBlockY() + 1, arenaCenter.getBlockZ()).setType(Material.LECTERN);
    }

    private void buildArenaBarriers(World world) {
        for (int angle = 0; angle < 360; angle += 30) {
            double radians = Math.toRadians(angle);
            int x = (int) (arenaCenter.getX() + 12 * Math.cos(radians));
            int z = (int) (arenaCenter.getZ() + 12 * Math.sin(radians));

            buildBarrierColumn(world, x, z);
        }
    }

    private void buildBarrierColumn(World world, int x, int z) {
        for (int y = 0; y <= 5; y++) {
            world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-6, 1, 0);
            spawn1.setYaw(90);
            player1.teleport(spawn1);
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(6, 1, 0);
            spawn2.setYaw(270);
            player2.teleport(spawn2);
        }
    }

    private void beginGame() {
        currentPhase = GamePhase.COUNTDOWN;
        sendGameInstructions();
        startCountdown();
    }

    private void sendGameInstructions() {
        String instructions = useFrench ? getFrenchInstructions() : getEnglishInstructions();
        MessageUtil.sendMessage(getPlayer1(), instructions);
        MessageUtil.sendMessage(getPlayer2(), instructions);
    }

    private String getFrenchInstructions() {
        return "&6=== Déduction Logique - Instructions ===\n" +
                "&fVous allez recevoir des questions de culture générale!\n" +
                "&fChaque question vaut des points basés sur la difficulté.\n" +
                "&fRépondez le plus rapidement possible pour des points bonus.\n" +
                "&fÉcrivez votre réponse dans le chat.\n" +
                "&fLa correspondance approximative est acceptée (90% de similitude).\n" +
                "&fVous avez 30 secondes par question.\n" +
                "&fLe joueur avec le plus de points gagne!";
    }

    private String getEnglishInstructions() {
        return "&6=== Logical Deduction - Instructions ===\n" +
                "&fYou will receive general knowledge questions!\n" +
                "&fEach question awards points based on difficulty.\n" +
                "&fAnswer as quickly as possible for bonus points.\n" +
                "&fType your answer in chat.\n" +
                "&fApproximate matching is accepted (90% similarity).\n" +
                "&fYou have 30 seconds per question.\n" +
                "&fPlayer with most points wins!";
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    String message = useFrench ?
                            "&eJeu commence dans " + countdown + "..." :
                            "&eGame starting in " + countdown + "...";

                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    countdown--;
                } else {
                    String message = useFrench ? "&aCOMMENCEZ! Première question arrive..." : "&aSTART! First question incoming...";
                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                    playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    startActiveGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startActiveGame() {
        currentPhase = GamePhase.PLAYING;
        startGameTimer();
        loadAndShowNextQuestion();
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            int timeLeft = GAME_DURATION / 20;

            @Override
            public void run() {
                if (currentPhase != GamePhase.PLAYING) {
                    this.cancel();
                    return;
                }

                timeLeft--;

                if (timeLeft <= 0) {
                    this.cancel();
                    endGameByTime();
                } else if (timeLeft % 60 == 0 || timeLeft <= 10) {
                    String message = useFrench ?
                            "&eTemps restant: " + timeLeft + " secondes" :
                            "&eTime remaining: " + timeLeft + " seconds";

                    MessageUtil.sendMessage(getPlayer1(), message);
                    MessageUtil.sendMessage(getPlayer2(), message);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void loadAndShowNextQuestion() {
        if (currentQuestionNumber >= MAX_QUESTIONS) {
            endGameByQuestions();
            return;
        }

        currentQuestionNumber++;
        questionActive = false;

        String loading = useFrench ? "&eChargement de la question " + currentQuestionNumber + "..." :
                "&eLoading question " + currentQuestionNumber + "...";
        MessageUtil.sendMessage(getPlayer1(), loading);
        MessageUtil.sendMessage(getPlayer2(), loading);

        fetchQuestionFromAPI().thenAccept(question -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (question != null) {
                    displayQuestion(question);
                } else {
                    loadFallbackQuestion();
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().warning("API Error: " + throwable.getMessage());
                loadFallbackQuestion();
            });
            return null;
        });
    }

    private CompletableFuture<Question> fetchQuestionFromAPI() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String difficulty = ThreadLocalRandom.current().nextBoolean() ? "easy" : "medium";
                String apiUrl = "https://opentdb.com/api.php?amount=1&difficulty=" + difficulty + "&type=multiple";

                HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "MinecraftLogicalDeduction/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("API returned status: " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return parseAPIResponse(response.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch question from API: " + e.getMessage());
                return null;
            }
        });
    }

    private Question parseAPIResponse(String jsonResponse) {
        try {
            JsonObject responseObj = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray results = responseObj.getAsJsonArray("results");

            if (results.size() == 0) {
                return null;
            }

            JsonObject questionObj = results.get(0).getAsJsonObject();

            String category = decodeHtml(questionObj.get("category").getAsString());
            String difficulty = questionObj.get("difficulty").getAsString();
            String questionText = decodeHtml(questionObj.get("question").getAsString());
            String correctAnswer = decodeHtml(questionObj.get("correct_answer").getAsString());

            List<String> incorrectAnswers = new ArrayList<>();
            JsonArray incorrectArray = questionObj.getAsJsonArray("incorrect_answers");
            for (int i = 0; i < incorrectArray.size(); i++) {
                incorrectAnswers.add(decodeHtml(incorrectArray.get(i).getAsString()));
            }

            return new Question(category, difficulty, questionText, correctAnswer, incorrectAnswers);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse API response: " + e.getMessage());
            return null;
        }
    }

    private String decodeHtml(String encoded) {
        return encoded
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'");
    }

    private void loadFallbackQuestion() {
        currentQuestion = getFallbackQuestion(currentQuestionNumber);
        displayQuestion(currentQuestion);
    }

    private Question getFallbackQuestion(int questionNumber) {
        Question[] fallbackQuestions = useFrench ? getFrenchFallbackQuestions() : getEnglishFallbackQuestions();
        return fallbackQuestions[(questionNumber - 1) % fallbackQuestions.length];
    }

    private Question[] getFrenchFallbackQuestions() {
        return new Question[] {
                new Question("Histoire", "medium", "Quelle année a marqué la chute du mur de Berlin?", "1989", Arrays.asList("1987", "1991", "1985")),
                new Question("Géographie", "easy", "Quelle est la capitale de l'Australie?", "Canberra", Arrays.asList("Sydney", "Melbourne", "Brisbane")),
                new Question("Sciences", "hard", "Quel élément chimique a le symbole Au?", "Or", Arrays.asList("Argent", "Aluminium", "Arsenic")),
                new Question("Littérature", "medium", "Qui a écrit 'Les Misérables'?", "Victor Hugo", Arrays.asList("Émile Zola", "Alexandre Dumas", "Gustave Flaubert")),
                new Question("Mathématiques", "easy", "Combien font 7 x 8?", "56", Arrays.asList("54", "58", "52"))
        };
    }

    private Question[] getEnglishFallbackQuestions() {
        return new Question[] {
                new Question("History", "medium", "In which year did World War II end?", "1945", Arrays.asList("1944", "1946", "1943")),
                new Question("Geography", "easy", "What is the capital of Japan?", "Tokyo", Arrays.asList("Osaka", "Kyoto", "Hiroshima")),
                new Question("Science", "hard", "What is the chemical symbol for gold?", "Au", Arrays.asList("Ag", "Al", "As")),
                new Question("Literature", "medium", "Who wrote 'Romeo and Juliet'?", "Shakespeare", Arrays.asList("Dickens", "Austen", "Byron")),
                new Question("Mathematics", "easy", "What is 9 x 7?", "63", Arrays.asList("56", "72", "54"))
        };
    }

    private void displayQuestion(Question question) {
        currentQuestion = question;
        questionActive = true;
        questionStartTime = System.currentTimeMillis();

        playerHasAnswered.put(player1UUID, false);
        playerHasAnswered.put(player2UUID, false);

        String questionHeader = useFrench ?
                "&6=== Question " + currentQuestionNumber + "/" + MAX_QUESTIONS + " ===" :
                "&6=== Question " + currentQuestionNumber + "/" + MAX_QUESTIONS + " ===";

        MessageUtil.sendMessage(getPlayer1(), questionHeader);
        MessageUtil.sendMessage(getPlayer2(), questionHeader);

        String categoryText = useFrench ? "&eCategorie: &f" : "&eCategory: &f";
        String difficultyText = useFrench ? "&eDifficulte: &f" : "&eDifficulty: &f";

        MessageUtil.sendMessage(getPlayer1(), categoryText + question.category);
        MessageUtil.sendMessage(getPlayer2(), categoryText + question.category);
        MessageUtil.sendMessage(getPlayer1(), difficultyText + question.difficulty);
        MessageUtil.sendMessage(getPlayer2(), difficultyText + question.difficulty);
        MessageUtil.sendMessage(getPlayer1(), "&f" + question.questionText);
        MessageUtil.sendMessage(getPlayer2(), "&f" + question.questionText);

        // Show multiple choice options
        List<String> allAnswers = new ArrayList<>();
        allAnswers.add(question.correctAnswer);
        allAnswers.addAll(question.incorrectAnswers);
        Collections.shuffle(allAnswers);

        this.allAnswers = allAnswers;

        for (int i = 0; i < allAnswers.size(); i++) {
            String option = (char)('A' + i) + ") " + allAnswers.get(i);
            MessageUtil.sendMessage(getPlayer1(), "&f" + option);
            MessageUtil.sendMessage(getPlayer2(), "&f" + option);
        }

        String promptText = useFrench ? "&aÉcrivez la lettre (A, B, C, D) dans le chat!" : "&aType the letter (A, B, C, D) in chat!";

        MessageUtil.sendMessage(getPlayer1(), promptText);
        MessageUtil.sendMessage(getPlayer2(), promptText);

        startQuestionTimer();
    }

    private void startQuestionTimer() {
        questionTimeRemaining = QUESTION_TIME;

        if (questionTimerTask != null) {
            questionTimerTask.cancel();
        }

        questionTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                questionTimeRemaining--;

                if (questionTimeRemaining <= 0) {
                    handleQuestionTimeout();
                    this.cancel();
                } else if (questionTimeRemaining <= 10) {
                    String warning = useFrench ?
                            "&c" + questionTimeRemaining + " secondes restantes!" :
                            "&c" + questionTimeRemaining + " seconds left!";

                    MessageUtil.sendMessage(getPlayer1(), warning);
                    MessageUtil.sendMessage(getPlayer2(), warning);
                    playSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 2.0f);
                } else if (questionTimeRemaining == 30) {
                    String warning = useFrench ? "&e30 secondes restantes!" : "&e30 seconds remaining!";
                    MessageUtil.sendMessage(getPlayer1(), warning);
                    MessageUtil.sendMessage(getPlayer2(), warning);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handleQuestionTimeout() {
        questionActive = false;

        String timeoutMsg = useFrench ?
                "&cTemps écoulé! La réponse était: &e" + currentQuestion.correctAnswer :
                "&cTime's up! The answer was: &e" + currentQuestion.correctAnswer;

        MessageUtil.sendMessage(getPlayer1(), timeoutMsg);
        MessageUtil.sendMessage(getPlayer2(), timeoutMsg);

        showCurrentScores();

        Bukkit.getScheduler().runTaskLater(plugin, this::loadAndShowNextQuestion, 60L);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (currentPhase != GamePhase.PLAYING || !questionActive) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!isParticipant(player)) return;

        event.setCancelled(true);

        String answer = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> processPlayerAnswer(playerUUID, answer));
    }

    private void processPlayerAnswer(UUID playerUUID, String answer) {
        if (!questionActive) return;

        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;

        if (playerHasAnswered.get(playerUUID)) {
            String alreadyMsg = useFrench ? "&cVous avez déjà répondu!" : "&cYou already answered!";
            MessageUtil.sendMessage(player, alreadyMsg);
            return;
        }

        playerHasAnswered.put(playerUUID, true);

        if (isAnswerCorrect(answer, currentQuestion.correctAnswer)) {
            handleCorrectAnswer(playerUUID, answer);
        } else {
            handleIncorrectAnswer(playerUUID, answer);
        }
    }

    private boolean isAnswerCorrect(String playerAnswer, String correctAnswer) {
        String letter = playerAnswer.trim().toUpperCase();
        if (letter.length() != 1 || !letter.matches("[A-D]")) {
            return false;
        }

        int choiceIndex = letter.charAt(0) - 'A';
        if (choiceIndex >= allAnswers.size()) {
            return false;
        }

        return allAnswers.get(choiceIndex).equals(correctAnswer);
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        return (maxLength - calculateLevenshteinDistance(s1, s2)) / (double) maxLength;
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }

        return costs[s2.length()];
    }

    private void handleCorrectAnswer(UUID playerUUID, String answer) {
        questionActive = false;

        if (questionTimerTask != null) {
            questionTimerTask.cancel();
        }

        Player player = Bukkit.getPlayer(playerUUID);
        Player opponent = playerUUID.equals(player1UUID) ? getPlayer2() : getPlayer1();

        long responseTime = System.currentTimeMillis() - questionStartTime;
        int points = calculatePoints(currentQuestion.difficulty, responseTime);

        playerScores.put(playerUUID, playerScores.get(playerUUID) + points);
        playerCorrectAnswers.put(playerUUID, playerCorrectAnswers.get(playerUUID) + 1);

        String correctMsg = useFrench ?
                "&a✓ Correct! +" + points + " points" :
                "&a✓ Correct! +" + points + " points";

        String opponentMsg = useFrench ?
                "&7" + (player != null ? player.getName() : "Adversaire") + " a trouvé la bonne réponse!" :
                "&7" + (player != null ? player.getName() : "Opponent") + " got the correct answer!";

        if (player != null) {
            MessageUtil.sendMessage(player, correctMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        if (opponent != null) {
            MessageUtil.sendMessage(opponent, opponentMsg);
        }

        showCurrentScores();

        Bukkit.getScheduler().runTaskLater(plugin, this::loadAndShowNextQuestion, 60L);
    }

    private void handleIncorrectAnswer(UUID playerUUID, String answer) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            String incorrectMsg = useFrench ?
                    "&c✗ Incorrect: " + answer :
                    "&c✗ Incorrect: " + answer;

            MessageUtil.sendMessage(player, incorrectMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private int calculatePoints(String difficulty, long responseTime) {
        int basePoints = getBasePoints(difficulty);
        double speedBonus = calculateSpeedBonus(responseTime);
        return (int) Math.round(basePoints * speedBonus);
    }

    private int getBasePoints(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "easy": return 10;
            case "medium": return 20;
            default: return 15;
        }
    }

    private double calculateSpeedBonus(long responseTime) {
        double seconds = responseTime / 1000.0;
        if (seconds <= 5) return 2.0;
        if (seconds <= 10) return 1.5;
        if (seconds <= 20) return 1.2;
        return 1.0;
    }

    private void showCurrentScores() {
        int player1Score = playerScores.get(player1UUID);
        int player2Score = playerScores.get(player2UUID);
        int player1Correct = playerCorrectAnswers.get(player1UUID);
        int player2Correct = playerCorrectAnswers.get(player2UUID);

        String scoresMsg = useFrench ? "&6=== Scores actuels ===" : "&6=== Current Scores ===";
        MessageUtil.sendMessage(getPlayer1(), scoresMsg);
        MessageUtil.sendMessage(getPlayer2(), scoresMsg);

        Player p1 = getPlayer1();
        Player p2 = getPlayer2();
        String p1Name = p1 != null ? p1.getName() : "Player 1";
        String p2Name = p2 != null ? p2.getName() : "Player 2";

        MessageUtil.sendMessage(getPlayer1(), "&e" + p1Name + ": &a" + player1Score + " points (" + player1Correct + " correct)");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p2Name + ": &a" + player2Score + " points (" + player2Correct + " correct)");
        MessageUtil.sendMessage(getPlayer1(), "&e" + p2Name + ": &a" + player2Score + " points (" + player2Correct + " correct)");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p1Name + ": &a" + player1Score + " points (" + player1Correct + " correct)");
    }

    private void endGameByQuestions() {
        String endMsg = useFrench ?
                "&6Toutes les questions terminées!" :
                "&6All questions completed!";

        MessageUtil.sendMessage(getPlayer1(), endMsg);
        MessageUtil.sendMessage(getPlayer2(), endMsg);

        determineWinner();
    }

    private void endGameByTime() {
        String endMsg = useFrench ?
                "&6Temps écoulé!" :
                "&6Time's up!";

        MessageUtil.sendMessage(getPlayer1(), endMsg);
        MessageUtil.sendMessage(getPlayer2(), endMsg);

        determineWinner();
    }

    private void determineWinner() {
        int player1Score = playerScores.get(player1UUID);
        int player2Score = playerScores.get(player2UUID);

        UUID winnerUUID;
        if (player1Score > player2Score) {
            winnerUUID = player1UUID;
        } else if (player2Score > player1Score) {
            winnerUUID = player2UUID;
        } else {
            int player1Correct = playerCorrectAnswers.get(player1UUID);
            int player2Correct = playerCorrectAnswers.get(player2UUID);

            if (player1Correct > player2Correct) {
                winnerUUID = player1UUID;
            } else if (player2Correct > player1Correct) {
                winnerUUID = player2UUID;
            } else {
                winnerUUID = ThreadLocalRandom.current().nextBoolean() ? player1UUID : player2UUID;
            }
        }

        showFinalResults();
        endGame(winnerUUID);
    }

    private void showFinalResults() {
        int player1Score = playerScores.get(player1UUID);
        int player2Score = playerScores.get(player2UUID);
        int player1Correct = playerCorrectAnswers.get(player1UUID);
        int player2Correct = playerCorrectAnswers.get(player2UUID);

        String finalMsg = useFrench ? "&6=== Résultats finaux ===" : "&6=== Final Results ===";
        MessageUtil.sendMessage(getPlayer1(), finalMsg);
        MessageUtil.sendMessage(getPlayer2(), finalMsg);

        Player p1 = getPlayer1();
        Player p2 = getPlayer2();
        String p1Name = p1 != null ? p1.getName() : "Player 1";
        String p2Name = p2 != null ? p2.getName() : "Player 2";

        MessageUtil.sendMessage(getPlayer1(), "&e" + p1Name + ": &a" + player1Score + " points (" + player1Correct + "/" + currentQuestionNumber + " correct)");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p2Name + ": &a" + player2Score + " points (" + player2Correct + "/" + currentQuestionNumber + " correct)");
        MessageUtil.sendMessage(getPlayer1(), "&e" + p2Name + ": &a" + player2Score + " points (" + player2Correct + "/" + currentQuestionNumber + " correct)");
        MessageUtil.sendMessage(getPlayer2(), "&e" + p1Name + ": &a" + player1Score + " points (" + player1Correct + "/" + currentQuestionNumber + " correct)");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentPhase != GamePhase.PLAYING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerUUID.equals(player1UUID) || playerUUID.equals(player2UUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentPhase != GamePhase.PLAYING) return;

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerUUID.equals(player1UUID) || playerUUID.equals(player2UUID)) {
            UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                String quitMsg = useFrench ?
                        "&aVotre adversaire a quitté le jeu! Vous gagnez!" :
                        "&aYour opponent has left the game! You win!";
                MessageUtil.sendMessage(winner, quitMsg);
            }

            endGame(winnerUUID);
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.playSound(player1.getLocation(), sound, volume, pitch);
        }
        if (player2 != null) {
            player2.playSound(player2.getLocation(), sound, volume, pitch);
        }
    }

    private boolean isParticipant(Player player) {
        return player.getUniqueId().equals(player1UUID) || player.getUniqueId().equals(player2UUID);
    }

    public void setLanguage(boolean french) {
        this.useFrench = french;
    }

    private void restorePlayerStates() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            // Remove any potion effects that might have been applied
            for (PotionEffect effect : player1.getActivePotionEffects()) {
                player1.removePotionEffect(effect.getType());
            }
        }

        if (player2 != null) {
            // Remove any potion effects that might have been applied
            for (PotionEffect effect : player2.getActivePotionEffects()) {
                player2.removePotionEffect(effect.getType());
            }
        }
    }



    @Override
    public void endGame(UUID winnerUUID) {
        if (currentPhase == GamePhase.ENDED) return;

        currentPhase = GamePhase.ENDED;

        if (questionTimerTask != null) {
            questionTimerTask.cancel();
            questionTimerTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null && player2 != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                String endMsg = useFrench ?
                        "&6Déduction Logique terminée! &aGagnant: &e" + winner.getName() :
                        "&6Logical Deduction has ended! &aWinner: &e" + winner.getName();

                MessageUtil.sendMessage(player1, endMsg);
                MessageUtil.sendMessage(player2, endMsg);
            }
        }

        restorePlayerStates(); // ADD THIS LINE

        HandlerList.unregisterAll(this);
        super.endGame(winnerUUID);
    }
}