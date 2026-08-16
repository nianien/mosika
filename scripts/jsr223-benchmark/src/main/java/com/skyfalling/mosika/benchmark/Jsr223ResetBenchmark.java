package com.skyfalling.mosika.benchmark;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import groovy.lang.GroovySystem;
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 8, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgsAppend = {
        "-Xms2g",
        "-Xmx2g",
        "-Dpolyglot.engine.WarnInterpreterOnly=false"
})
public class Jsr223ResetBenchmark {

    @Benchmark
    public Object resetBindingsOnly(EngineState state) {
        state.resetBindings();
        return state.bindings;
    }

    @Benchmark
    public Object compiledEvalWithoutReset(EngineState state) throws ScriptException {
        return state.compiledScript.eval();
    }

    @Benchmark
    public Object compiledEvalAfterBindingsReset(EngineState state) throws ScriptException {
        state.resetBindings();
        return state.compiledScript.eval();
    }

    @Benchmark
    public Object compiledEvalWithFreshBindings(EngineState state) throws ScriptException {
        return state.compiledScript.eval(state.freshBindings());
    }

    @Benchmark
    public Object sharedEngineFreshBindings(SharedEngineState state) throws ScriptException {
        return state.compiledScript.eval(state.freshBindings());
    }

    @State(Scope.Benchmark)
    public static class SharedEngineState extends EngineState {
    }

    @State(Scope.Thread)
    public static class EngineState {

        @Param({"graal", "rhino", "nashorn", "groovy"})
        public String engineName;

        @Param({"constant", "simpleRule", "nestedMap", "javaCall", "pureLoop", "interopLoop"})
        public String workloadName;

        @Param({"0", "50"})
        public int udfCount;

        private final Map<String, Object> root = Map.ofEntries(
                Map.entry("age", 20),
                Map.entry("enabled", true),
                Map.entry("score", 85),
                Map.entry("amount", 90),
                Map.entry("fee", 5),
                Map.entry("name", "mosika-rule-engine"),
                Map.entry("user", Map.of(
                        "address", Map.of("city", "Hangzhou"),
                        "level", 4)),
                Map.entry("scores", List.of(90, 80, 85)),
                Map.entry("values", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
        );

        private final Map<String, Object> ruleContext = Map.of("owner", "system");

        private final Map<String, Object> arguments = Map.of("ratio", 2);

        private final Helper helper = new Helper();

        private final String[] udfNames = new String[50];

        private ScriptEngine scriptEngine;

        public CompiledScript compiledScript;

        public Bindings bindings;

        private Engine graalEngine;

        @Setup(Level.Trial)
        public void setup() throws ScriptException {
            for (int i = 0; i < udfNames.length; i++) {
                udfNames[i] = "udf" + i;
            }
            scriptEngine = createEngine(engineName);
            Workload workload = workload(workloadName);
            compiledScript = ((Compilable) scriptEngine).compile(workload.source);
            bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
            resetBindings();
            Object result = compiledScript.eval();
            if (!workload.matches(result)) {
                throw new IllegalStateException(engineName + "/" + workloadName
                        + " returned " + result + ", expected " + workload.expected);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            closeEngine(scriptEngine, graalEngine);
        }

        public void resetBindings() {
            bindings.remove("total");
            bindings.remove("i");
            putBindings(bindings);
        }

        public Bindings freshBindings() {
            Bindings fresh = scriptEngine.createBindings();
            putBindings(fresh);
            return fresh;
        }

        private void putBindings(Bindings target) {
            target.put("root", root);
            target.put("ruleContext", ruleContext);
            target.put("ruleArgs", arguments);
            target.put("helper", helper);
            for (int i = 0; i < udfCount; i++) {
                target.put(udfNames[i], helper);
            }
        }

        private ScriptEngine createEngine(String name) {
            switch (name) {
                case "graal":
                    graalEngine = Engine.create();
                    return GraalJSScriptEngine.create(
                            graalEngine,
                            Context.newBuilder("js")
                                    .allowExperimentalOptions(true)
                                    .allowHostAccess(HostAccess.ALL)
                                    .allowHostClassLookup(className -> true)
                                    .option("js.ecmascript-version", "latest"));
                case "rhino":
                    return new RhinoScriptEngineFactory().getScriptEngine();
                case "nashorn":
                    return new NashornScriptEngineFactory().getScriptEngine("--language=es6");
                case "groovy":
                    ScriptEngine groovy = new GroovyScriptEngineFactory().getScriptEngine();
                    if (!GroovySystem.getVersion().startsWith("5.")) {
                        throw new IllegalStateException("Unexpected Groovy version: " + GroovySystem.getVersion());
                    }
                    return groovy;
                default:
                    throw new IllegalArgumentException("Unknown engine: " + name);
            }
        }
    }

    public static final class Helper {

        public int weightedScore(int amount, int level) {
            return amount * level;
        }
    }

    private static Workload workload(String name) {
        switch (name) {
            case "constant":
                return new Workload("1", 1);
            case "simpleRule":
                return new Workload(
                        "root.get('age') >= 18 && root.get('enabled') && root.get('score') > 60", true);
            case "nestedMap":
                return new Workload(
                        "root.get('user').get('address').get('city') == 'Hangzhou'"
                                + " && root.get('user').get('level') >= 3",
                        true);
            case "javaCall":
                return new Workload(
                        "helper.weightedScore(root.get('amount'), root.get('user').get('level')) >= 360",
                        true);
            case "pureLoop":
                return new Workload(
                        "total = 0;\n"
                                + "for (i = 0; i < 100; i++) {\n"
                                + "    total += i;\n"
                                + "}\n"
                                + "total",
                        4950);
            case "interopLoop":
                return new Workload(
                        "total = 0;\n"
                                + "for (i = 0; i < 100; i++) {\n"
                                + "    total += root.get('values')[i % root.get('values').length];\n"
                                + "}\n"
                                + "total",
                        550);
            default:
                throw new IllegalArgumentException("Unknown workload: " + name);
        }
    }

    private static void closeEngine(ScriptEngine scriptEngine, Engine graalEngine) {
        if (scriptEngine instanceof GraalJSScriptEngine) {
            ((GraalJSScriptEngine) scriptEngine).getPolyglotContext().close();
        }
        if (graalEngine != null) {
            graalEngine.close();
        }
    }

    private static final class Workload {

        private final String source;

        private final Object expected;

        private Workload(String source, Object expected) {
            this.source = source;
            this.expected = expected;
        }

        private boolean matches(Object actual) {
            if (expected instanceof Number && actual instanceof Number) {
                return Double.compare(
                        ((Number) expected).doubleValue(),
                        ((Number) actual).doubleValue()) == 0;
            }
            return expected.equals(actual);
        }
    }
}
