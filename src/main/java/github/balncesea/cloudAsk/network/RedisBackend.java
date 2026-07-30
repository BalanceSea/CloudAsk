package github.balncesea.cloudAsk.network;

import com.google.gson.Gson;
import github.balncesea.cloudAsk.model.NetworkEvent;
import github.balncesea.cloudAsk.model.Question;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

/** Redis implementation. The Lua scripts make starting, answering and cancelling single-writer operations. */
public final class RedisBackend implements NetworkBackend {
    private static final String START_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('HSET', KEYS[1],
              'id', ARGV[1], 'text', ARGV[2], 'answer', ARGV[3], 'normalizedAnswer', ARGV[4],
              'askerUuid', ARGV[5], 'askerName', ARGV[6], 'sourceServer', ARGV[7],
              'rewardCommands', ARGV[8], 'createdAt', ARGV[9], 'expiresAt', ARGV[10])
            redis.call('PEXPIRE', KEYS[1], ARGV[11])
            redis.call('PUBLISH', KEYS[2], ARGV[12])
            return 1
            """;
    private static final String ANSWER_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'id') ~= ARGV[1] then return 0 end
            if redis.call('HGET', KEYS[1], 'winnerUuid') then return 2 end
            if redis.call('HGET', KEYS[1], 'normalizedAnswer') ~= ARGV[2] then return 1 end
            redis.call('HSET', KEYS[1], 'winnerUuid', ARGV[3], 'winnerName', ARGV[4],
              'winnerServer', ARGV[5], 'winnerInstanceId', ARGV[6])
            redis.call('PUBLISH', KEYS[2], ARGV[7])
            redis.call('DEL', KEYS[1])
            return 3
            """;
    private static final String CANCEL_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'id') ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('PUBLISH', KEYS[2], ARGV[2])
            return 1
            """;

    private final JavaPlugin plugin;
    private final JedisPool pool;
    private final ExecutorService ioExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Gson gson = new Gson();
    private final String key;
    private final String channel;
    private volatile JedisPubSub subscription;
    private volatile Consumer<NetworkEvent> eventHandler = event -> { };

    public RedisBackend(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        String namespace = config.getString("redis.namespace", "cloudask");
        if (namespace == null || namespace.isBlank()) {
            namespace = "cloudask";
        }
        namespace = namespace.trim();
        this.key = namespace + ":active";
        this.channel = namespace + ":events";

        String host = config.getString("redis.host", "127.0.0.1");
        int port = config.getInt("redis.port", 6379);
        String username = config.getString("redis.username", "");
        String password = config.getString("redis.password", "");
        int timeout = Math.max(500, config.getInt("redis.timeout-millis", 3000));
        boolean ssl = config.getBoolean("redis.ssl", false);

        DefaultJedisClientConfig.Builder clientBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout)
                .database(config.getInt("redis.database", 0))
                .ssl(ssl);
        if (username != null && !username.isBlank()) {
            clientBuilder.user(username.trim());
        }
        if (password != null && !password.isBlank()) {
            clientBuilder.password(password);
        }
        this.pool = new JedisPool(new JedisPoolConfig(), new HostAndPort(host, port), clientBuilder.build());
        this.ioExecutor = Executors.newFixedThreadPool(2, daemonFactory("CloudAsk-Redis"));
    }

    @Override
    public void start(Consumer<NetworkEvent> eventHandler) {
        this.eventHandler = eventHandler;
        ioExecutor.execute(this::subscribeLoop);
    }

    @Override
    public CompletableFuture<StartResult> startQuestion(Question question) {
        return supply(() -> {
            NetworkEvent event = NetworkEvent.started(question);
            long ttl = Math.max(1000, question.expiresAt() - System.currentTimeMillis());
            try (Jedis jedis = pool.getResource()) {
                Object result = jedis.eval(
                        START_SCRIPT,
                        List.of(key, channel),
                        List.of(question.id(), question.text(), question.answer(), question.normalizedAnswer(),
                                question.askerUuid(), question.askerName(), question.sourceServer(),
                                gson.toJson(question.rewardCommands()), Long.toString(question.createdAt()),
                                Long.toString(question.expiresAt()),
                                Long.toString(ttl), gson.toJson(event)));
                if (Long.valueOf(1L).equals(result)) {
                    return StartResult.STARTED;
                }
                return StartResult.BUSY;
            }
        });
    }

    @Override
    public CompletableFuture<AnswerResult> submitAnswer(
            String questionId, String normalizedAnswer, String playerUuid, String playerName,
            String serverId, String instanceId) {
        return supply(() -> {
            long now = System.currentTimeMillis();
            Optional<Question> current = readCurrent();
            if (current.isEmpty() || !current.get().id().equals(questionId)) {
                return AnswerResult.STALE;
            }
            Question question = current.get();
            NetworkEvent event = NetworkEvent.answered(
                    question, playerUuid, playerName, serverId, instanceId, now);
            try (Jedis jedis = pool.getResource()) {
                Object result = jedis.eval(
                        ANSWER_SCRIPT,
                        List.of(key, channel),
                        List.of(questionId, normalizedAnswer, playerUuid, playerName, serverId,
                                instanceId, gson.toJson(event)));
                return switch (((Number) result).intValue()) {
                    case 3 -> AnswerResult.WON;
                    case 2 -> AnswerResult.ALREADY_ANSWERED;
                    case 1 -> AnswerResult.INCORRECT;
                    default -> AnswerResult.STALE;
                };
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> cancelQuestion(String questionId) {
        return supply(() -> {
            Optional<Question> current = readCurrent();
            if (current.isEmpty() || !current.get().id().equals(questionId)) {
                return false;
            }
            NetworkEvent event = NetworkEvent.cancelled(current.get());
            try (Jedis jedis = pool.getResource()) {
                Object result = jedis.eval(CANCEL_SCRIPT, List.of(key, channel), List.of(questionId, gson.toJson(event)));
                return Long.valueOf(1L).equals(result);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Question>> currentQuestion() {
        return supply(this::readCurrent);
    }

    private Optional<Question> readCurrent() {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> values = jedis.hgetAll(key);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            String rewardsJson = values.get("rewardCommands");
            String[] rewardCommands = rewardsJson == null || rewardsJson.isBlank()
                    ? new String[0]
                    : gson.fromJson(rewardsJson, String[].class);
            Question question = new Question(
                    values.get("id"), values.get("text"), values.get("answer"), values.get("normalizedAnswer"),
                    values.get("askerUuid"), values.get("askerName"), values.get("sourceServer"),
                    rewardCommands == null ? List.of() : List.of(rewardCommands),
                    Long.parseLong(values.get("createdAt")), Long.parseLong(values.get("expiresAt")));
            return question.expiresAt() > System.currentTimeMillis() ? Optional.of(question) : Optional.empty();
        }
    }

    private void subscribeLoop() {
        while (!closed.get()) {
            try (Jedis jedis = pool.getResource()) {
                JedisPubSub listener = new JedisPubSub() {
                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        if (!closed.get()) {
                            ioExecutor.execute(() -> {
                                try {
                                    readCurrent().ifPresent(question -> eventHandler.accept(NetworkEvent.started(question)));
                                } catch (RuntimeException ex) {
                                    plugin.getLogger().log(Level.WARNING, "Redis 重连后同步当前问题失败", ex);
                                }
                            });
                        }
                    }

                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            eventHandler.accept(gson.fromJson(message, NetworkEvent.class));
                        } catch (RuntimeException ex) {
                            plugin.getLogger().log(Level.WARNING, "无法解析 Redis 问答事件", ex);
                        }
                    }
                };
                subscription = listener;
                jedis.subscribe(listener, this.channel);
            } catch (Exception ex) {
                if (!closed.get()) {
                    plugin.getLogger().log(Level.WARNING, "Redis 订阅断开，2 秒后重连: " + ex.getMessage());
                    sleepQuietly(2000);
                }
            } finally {
                subscription = null;
            }
        }
    }

    private <T> CompletableFuture<T> supply(java.util.concurrent.Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, ioExecutor);
    }

    private static ThreadFactory daemonFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix);
            thread.setDaemon(true);
            return thread;
        };
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        JedisPubSub currentSubscription = subscription;
        if (currentSubscription != null) {
            try {
                currentSubscription.unsubscribe();
            } catch (Exception ignored) {
                // The Redis socket may already be closed during shutdown.
            }
        }
        ioExecutor.shutdownNow();
        pool.close();
    }
}
