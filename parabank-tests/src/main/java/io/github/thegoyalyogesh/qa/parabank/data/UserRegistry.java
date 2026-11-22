package io.github.thegoyalyogesh.qa.parabank.data;

import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of customers registered during this JVM run, shared by all parallel threads.
 * {@link ConcurrentHashMap} gives thread-safe put/get without a global lock.
 * Plan 03 uses it to pick user A and user B for cross-user payments.
 */
public final class UserRegistry {

    private static final ConcurrentHashMap<String, ParaBankUser> USERS = new ConcurrentHashMap<>();

    private UserRegistry() {
    }

    public static void register(ParaBankUser user) {
        USERS.put(user.userId(), user);
    }

    public static Optional<ParaBankUser> find(String userId) {
        return Optional.ofNullable(USERS.get(userId));
    }

    /** Snapshot sorted by user id. */
    public static List<ParaBankUser> all() {
        return USERS.values().stream().sorted(Comparator.comparing(ParaBankUser::userId)).toList();
    }

    public static int size() {
        return USERS.size();
    }

    public static void clear() {
        USERS.clear();
    }
}
