package org.ulpgc.tarantino.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ulpgc.tarantino.common.model.WorkerIdentity;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarantinoConfigTest {

    @Test
    void takesTheDefaultsOfTheSpec() {
        TarantinoConfig config = TarantinoConfig.from(Map.of("HOSTNAME", "n3"), TarantinoService.WORKER);

        assertEquals(new TarantinoConfig(7000, OptionalInt.empty(), "mongodb://localhost:27017", List.of("localhost:5701"),
                "tarantino", Path.of("datalake"), Path.of("workload"), Optional.empty(), "sample_ids.txt", 8, 3, 100,
                Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ofSeconds(30),
                Duration.ofSeconds(120), Duration.ofSeconds(600), Duration.ofSeconds(600), Duration.ofSeconds(3600),
                Duration.ofSeconds(5), "*", Optional.empty(), IndexDesign.LOCAL, "n3", "n3-w1", Optional.empty(),
                List.of("crawler", "indexer", "query"), 5701, Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(10),
                Duration.ofSeconds(5)), config);
    }

    @Test
    void givesEachServiceItsOwnDefaultPort() {
        assertEquals(7001, TarantinoConfig.from(Map.of(), TarantinoService.CRAWLER).port());
        assertEquals(7002, TarantinoConfig.from(Map.of(), TarantinoService.INDEXER).port());
        assertEquals(7003, TarantinoConfig.from(Map.of(), TarantinoService.QUERY).port());
        assertEquals(7004, TarantinoConfig.from(Map.of(), TarantinoService.CONTROL).port());
    }

    @Test
    void readsTheVariablesTrimmedAndTreatsBlankOnesAsUnset() {
        TarantinoConfig config = TarantinoConfig.from(Map.of(
                "TARANTINO_HAZELCAST", " hazelcast:5701 , backup:5701 ",
                "TARANTINO_HTTP_THREADS", " 16 ",
                "TARANTINO_MIRROR", "/mirror",
                "TARANTINO_CANDIDATES", "mirror",
                "TARANTINO_CORS_ORIGIN", " "), TarantinoService.CONTROL);

        assertEquals(List.of("hazelcast:5701", "backup:5701"), config.hazelcastMembers());
        assertEquals(OptionalInt.of(16), config.httpThreads());
        assertEquals(Optional.of(Path.of("/mirror")), config.mirror());
        assertEquals("mirror", config.candidates());
        assertEquals("*", config.corsOrigin());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TARANTINO_PORT", "TARANTINO_HTTP_THREADS", "TARANTINO_PARALLEL_DOWNLOADS",
            "TARANTINO_MAX_ATTEMPTS", "TARANTINO_INDEX_BATCH", "TARANTINO_BATCH_TIMEOUT_SECONDS", "TARANTINO_LEASE_SECONDS",
            "TARANTINO_HEARTBEAT_SECONDS", "TARANTINO_RECONCILE_SECONDS", "TARANTINO_REQUEUE_SECONDS", "TARANTINO_GC_SECONDS",
            "TARANTINO_TMP_MAX_AGE_SECONDS", "TARANTINO_CLEANUP_SECONDS", "TARANTINO_SNAPSHOT_SECONDS", "TARANTINO_HAZELCAST_PORT",
            "TARANTINO_HAZELCAST_FAILURE_SECONDS", "TARANTINO_WORKER_HEARTBEAT_SECONDS", "TARANTINO_WORKER_TIMEOUT_SECONDS",
            "TARANTINO_PEER_TIMEOUT_SECONDS"})
    void rejectsAnyNumberThatIsNotAPositiveIntegerNamingItsVariable(String variable) {
        for (String value : List.of("0", "-5", "many", "1.5", "99999999999")) {
            assertInvalid(variable, Map.of(variable, value), TarantinoService.CRAWLER);
        }
    }

    @Test
    void rejectsAnEmptyListOfHazelcastMembers() {
        assertInvalid("TARANTINO_HAZELCAST", Map.of("TARANTINO_HAZELCAST", " , "), TarantinoService.QUERY);
    }

    @Test
    void renewsALeaseAtLeastThreeTimes() {
        TarantinoConfig.from(Map.of("TARANTINO_HEARTBEAT_SECONDS", "20"), TarantinoService.CRAWLER);
        assertInvalid("TARANTINO_HEARTBEAT_SECONDS", Map.of("TARANTINO_HEARTBEAT_SECONDS", "21"), TarantinoService.CRAWLER);
    }

    @Test
    void waitsTenLeasesBeforeDeletingBatchesOrTemporaryFiles() {
        assertInvalid("TARANTINO_GC_SECONDS", Map.of("TARANTINO_GC_SECONDS", "599"), TarantinoService.CONTROL);
        assertInvalid("TARANTINO_TMP_MAX_AGE_SECONDS", Map.of("TARANTINO_TMP_MAX_AGE_SECONDS", "599"), TarantinoService.CONTROL);
    }

    @Test
    void requeuesOnlyAfterABatchCouldHaveClosed() {
        assertInvalid("TARANTINO_REQUEUE_SECONDS", Map.of("TARANTINO_REQUEUE_SECONDS", "30"), TarantinoService.CONTROL);
    }

    @Test
    void seedsTheMirrorOnlyWhenThereIsOne() {
        assertInvalid("TARANTINO_CANDIDATES", Map.of("TARANTINO_CANDIDATES", "mirror"), TarantinoService.CONTROL);
    }

    @Test
    void crashesOnlyAtAPointOfTheServiceBeingStarted() {
        assertEquals(Optional.of(CrashPoint.AFTER_CLAIM),
                TarantinoConfig.from(Map.of("TARANTINO_CRASH_AT", "after-claim"), TarantinoService.CRAWLER).crashAt());
        assertEquals(Optional.of(CrashPoint.INDEXER_AFTER_DEQUEUE),
                TarantinoConfig.from(Map.of("TARANTINO_CRASH_AT", "after-dequeue"), TarantinoService.INDEXER).crashAt());
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "after-publish"), TarantinoService.CRAWLER);
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "after-dequeue"), TarantinoService.CONTROL);
        assertInvalid("TARANTINO_CRASH_AT", Map.of("TARANTINO_CRASH_AT", "nowhere"), TarantinoService.INDEXER);
    }

    @Test
    void namesTheFirstWorkerOfItsNodeAndAdvertisesTheGivenHost() {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_NODE", "n2", "TARANTINO_ADVERTISE_HOST", "10.0.0.12"),
                TarantinoService.WORKER);

        assertEquals(new WorkerIdentity("n2-w1", "n2"), config.identity());
        assertEquals("10.0.0.12", config.advertisedHost());
    }

    @Test
    void takesAnyRolesWithoutRepeatsAndAnEmptyListWhenTheVariableIsBlank() {
        assertEquals(List.of("query", "crawler"),
                TarantinoConfig.from(Map.of("TARANTINO_ROLES", " query , crawler "), TarantinoService.WORKER).roles());
        assertEquals(List.of(), TarantinoConfig.from(Map.of("TARANTINO_ROLES", " "), TarantinoService.WORKER).roles());
        assertInvalid("TARANTINO_ROLES", Map.of("TARANTINO_ROLES", "indexer,indexer"), TarantinoService.WORKER);
        assertInvalid("TARANTINO_ROLES", Map.of("TARANTINO_ROLES", "crawler,control"), TarantinoService.WORKER);
    }

    @Test
    void readsTheIndexDesignInLowerCase() {
        assertEquals(IndexDesign.GRID,
                TarantinoConfig.from(Map.of("TARANTINO_INDEX_DESIGN", " grid "), TarantinoService.WORKER).indexDesign());
        assertInvalid("TARANTINO_INDEX_DESIGN", Map.of("TARANTINO_INDEX_DESIGN", "GRID"), TarantinoService.WORKER);
    }

    @Test
    void beatsAsAWorkerAtLeastThreeTimesBeforeItCountsAsDead() {
        TarantinoConfig.from(Map.of("TARANTINO_WORKER_HEARTBEAT_SECONDS", "3", "TARANTINO_WORKER_TIMEOUT_SECONDS", "9"),
                TarantinoService.WORKER);
        assertInvalid("TARANTINO_WORKER_HEARTBEAT_SECONDS", Map.of("TARANTINO_WORKER_HEARTBEAT_SECONDS", "4"), TarantinoService.WORKER);
    }

    @Test
    void ignoresLeftoverVariablesOfTheCoordinationModes() {
        TarantinoConfig config = TarantinoConfig.from(Map.of("TARANTINO_COORDINATION", "kafka", "TARANTINO_POLL_MILLIS", "0",
                "TARANTINO_SNAPSHOT_ON_PUBLISH", "maybe"), TarantinoService.QUERY);

        assertEquals(TarantinoConfig.from(Map.of(), TarantinoService.QUERY), config);
    }

    private static void assertInvalid(String variable, Map<String, String> environment, TarantinoService service) {
        InvalidConfigurationException error = assertThrows(InvalidConfigurationException.class,
                () -> TarantinoConfig.from(environment, service), environment::toString);
        assertEquals(variable, error.variable(), environment::toString);
        assertEquals(variable, error.getMessage().split(" ")[0]);
    }
}
