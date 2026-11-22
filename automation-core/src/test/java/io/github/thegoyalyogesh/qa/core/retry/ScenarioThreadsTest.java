package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.ITestContext;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ScenarioThreadsTest {

    @Test
    public void defaultsToThreeWhenNotSet() {
        assertThat(ScenarioThreads.resolve(null)).isEqualTo(3);
        assertThat(ScenarioThreads.resolve(" ")).isEqualTo(3);
    }

    @Test
    public void readsTheValue() {
        assertThat(ScenarioThreads.resolve(" 5 ")).isEqualTo(5);
    }

    @Test
    public void rejectsNonsense() {
        assertThatThrownBy(() -> ScenarioThreads.resolve("0")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threads");
        assertThatThrownBy(() -> ScenarioThreads.resolve("many")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Proves the setting really limits a parallel data provider when TestNG is started without Maven (as an IDE does). */
    @Test
    public void limitsParallelRowsWhenTestNgIsStartedDirectly() {
        String before = System.getProperty("threads");
        System.setProperty("threads", "2");
        try {
            ParallelProbe.reset();
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[]{ParallelProbe.class});
            testng.setVerbose(0);
            testng.run();
            assertThat(testng.hasFailure()).isFalse();
            assertThat(ParallelProbe.maxConcurrent.get()).isEqualTo(2);
        } finally {
            if (before == null) System.clearProperty("threads"); else System.setProperty("threads", before);
        }
    }

    /** Stand-in for a Cucumber runner: 6 parallel rows that each stay busy long enough to overlap. */
    public static class ParallelProbe {
        static final AtomicInteger running = new AtomicInteger();
        static final AtomicInteger maxConcurrent = new AtomicInteger();

        static void reset() {
            running.set(0);
            maxConcurrent.set(0);
        }

        @BeforeClass(alwaysRun = true)
        public void limit(ITestContext context) {
            ScenarioThreads.apply(context);
        }

        @DataProvider(parallel = true)
        public Object[][] rows() {
            return new Object[][]{{1}, {2}, {3}, {4}, {5}, {6}};
        }

        @Test(dataProvider = "rows")
        public void row(int ignored) throws InterruptedException {
            maxConcurrent.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(150);
            running.decrementAndGet();
        }
    }
}
