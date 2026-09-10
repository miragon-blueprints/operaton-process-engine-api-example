package io.miragon.common.architecture;

import org.junit.jupiter.api.Nested;

/**
 * A single, ready-to-use architecture suite for a service — combining <b>ArchUnit</b> and the
 * source-level lint, each doing what it is best at.
 *
 * <p>A service wires up the full suite with one line:
 *
 * <pre>{@code
 * class ArchitectureTest extends ServiceArchitectureTest {
 *     ArchitectureTest() {
 *         super("io.miragon.blueprint");
 *     }
 * }
 * }</pre>
 *
 * <h2>Why a mix, and who owns what</h2>
 *
 * <ul>
 *   <li><b>ArchUnit</b> reads compiled <b>bytecode</b>, so it sees the fully resolved dependency
 *       graph. It owns the <i>dependency &amp; structure</i> rules — hexagonal layering,
 *       technology-neutrality of domain &amp; application, port/adapter isolation
 *       ({@link Dependencies}), naming conventions ({@link Naming}), freedom of cycles and the
 *       no-standard-streams check ({@link CodingGuidelines}).
 *   <li><b>Checkstyle</b> reads Java <b>source</b>, so it sees things the compiler erases or
 *       tolerates. It owns the <i>source-structure</i> rules — one top-level declaration per file
 *       (SRP) and no wildcard imports. It runs in the same build gate (see the shared
 *       {@code checkstyle.xml} at the repo root and ADR-0014).
 * </ul>
 *
 * <p>This module is <b>self-contained</b>: it carries the ArchUnit dependency and its own copies of
 * the rules, so it can be dropped into a service as a single test dependency.
 */
public abstract class ServiceArchitectureTest {

    private final String rootPackage;

    protected ServiceArchitectureTest(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    @Nested
    public class Dependencies extends HexagonalArchitectureTest {
        public Dependencies() {
            super(rootPackage);
        }
    }

    @Nested
    public class Naming extends NamingConventionArchitectureTest {
        public Naming() {
            super(rootPackage);
        }
    }

    @Nested
    public class CodingGuidelines extends BasicCodingGuidelinesTest {
        public CodingGuidelines() {
            super(rootPackage);
        }
    }
}
