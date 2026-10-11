package be.reeks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Lokale webserver: pagina, start/stop van de solver en voortgang. Luistert enkel op localhost. */
public final class Main {

    private static SolveJob job;
    private static Series series;
    private static String seriesText;
    private static int[] scores = Rules.DEFAULT_SCORE.clone();
    private static int seconds = 60;
    private static String seriesError;
    private static boolean[] never = defaultNever();

    private static boolean[] defaultNever() {
        boolean[] n = new boolean[Rules.Z];
        n[4] = true; n[11] = true;       // halve weekends en twee weekends na elkaar
        return n;
    }

    private static boolean[] parseNever(String s) {
        if (s == null || s.length() != Rules.Z) return defaultNever();
        boolean[] n = new boolean[Rules.Z];
        for (int i = 0; i < Rules.Z; i++) n[i] = s.charAt(i) == '1';
        n[9] = false;
        return n;
    }

    public static void main(String[] args) throws Exception {
        boolean gui = false;
        for (String a : args) if (a.equals("gui") || a.equals("--gui")) gui = true;
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        // In de container: 0.0.0.0 (podman publiceert enkel op 127.0.0.1). Als app op je eigen pc: enkel localhost.
        String bind = gui ? "127.0.0.1" : System.getenv().getOrDefault("BIND", "0.0.0.0");
        HttpServer server = null;
        for (int i = 0; i < (gui ? 20 : 1) && server == null; i++) {
            try {
                server = HttpServer.create(new InetSocketAddress(bind, port + i), 0);
                port = port + i;
            } catch (java.net.BindException e) {
                if (!gui) throw e;
            }
        }
        if (server == null) throw new java.net.BindException("Geen vrije poort gevonden vanaf " + port);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", Main::root);
        server.createContext("/api/default", ex -> send(ex, 200, "text/plain; charset=utf-8", resource("/reeks_A.txt")));
        server.createContext("/api/check", Main::check);
        server.createContext("/api/start", Main::start);
        server.createContext("/api/stop", ex -> { synchronized (Main.class) { if (job != null) job.stop(); } send(ex, 200, "application/json", "{\"ok\":true}"); });
        server.createContext("/api/status", ex -> send(ex, 200, "application/json; charset=utf-8", status()));
        server.start();
        System.out.println("Reeksoptimalisatie draait op http://localhost:" + port);
        if (gui) Gui.show("http://localhost:" + port);
    }

    private static void root(HttpExchange ex) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) { send(ex, 404, "text/plain", "niet gevonden"); return; }
        try (InputStream in = Main.class.getResourceAsStream("/web" + path)) {
            if (in == null) { send(ex, 404, "text/plain", "niet gevonden"); return; }
            byte[] b = in.readAllBytes();
            String type = path.endsWith(".html") ? "text/html; charset=utf-8"
                    : path.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : path.endsWith(".txt") ? "text/plain; charset=utf-8" : "application/octet-stream";
            ex.getResponseHeaders().set("Content-Type", type);
            ex.getResponseHeaders().set("Cache-Control", path.startsWith("/vendor/") ? "max-age=86400" : "no-store");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        }
    }

    private static String resource(String path) {
        try (InputStream in = Main.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    /** Body: regels "sleutel=waarde", dan een regel "---", dan de reeks. */
    private static Map<String, String> parseBody(String body, StringBuilder seriesOut) {
        Map<String, String> m = new HashMap<>();
        String[] lines = body.split("\\R");
        int i = 0;
        for (; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) { i++; break; }
            int eq = lines[i].indexOf('=');
            if (eq > 0) m.put(lines[i].substring(0, eq).trim(), lines[i].substring(eq + 1).trim());
        }
        for (; i < lines.length; i++) seriesOut.append(lines[i]).append('\n');
        return m;
    }

    private static int[] parseScores(String s) {
        if (s == null) return Rules.DEFAULT_SCORE.clone();
        int[] r = Arrays.stream(s.split(",")).mapToInt(x -> Math.max(0, Math.min(10, Integer.parseInt(x.trim())))).toArray();
        if (r.length != Rules.Z) throw new IllegalArgumentException("Verwacht " + Rules.Z + " scores");
        return r;
    }

    private static synchronized void check(HttpExchange ex) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, String> m = parseBody(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), sb);
        try {
            scores = parseScores(m.get("scores"));
            never = parseNever(m.get("never"));
            if (job == null || job.state != SolveJob.State.RUNNING) {
                series = Series.parse(sb.toString());
                seriesText = sb.toString();
                seriesError = null;
                job = null;
            }
        } catch (Exception e) { seriesError = e.getMessage(); }
        send(ex, 200, "application/json; charset=utf-8", status());
    }

    private static synchronized void start(HttpExchange ex) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, String> m = parseBody(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), sb);
        try {
            if (job != null && job.state == SolveJob.State.RUNNING) throw new IllegalStateException("Er loopt al een optimalisatie");
            Series s = Series.parse(sb.toString());
            int[] sc = parseScores(m.get("scores"));
            never = parseNever(m.get("never"));
            int secs = Math.max(5, Math.min(3600, Integer.parseInt(m.getOrDefault("seconds", "60"))));
            long seed = Long.parseLong(m.getOrDefault("seed", String.valueOf(System.nanoTime() % 100000)));
            int[] startGrid = s.original;
            boolean fromBest = "best".equals(m.get("from")) && job != null && sb.toString().equals(seriesText) && job.bestGrid != null;
            if (fromBest) startGrid = job.bestGrid;
            series = s; seriesText = sb.toString(); scores = sc; seconds = secs; seriesError = null;
            job = new SolveJob(s, sc, secs, seed, startGrid);
            int cores = Runtime.getRuntime().availableProcessors();
            job.threads = Math.max(1, Math.min(16, Integer.parseInt(m.getOrDefault("threads", String.valueOf(Math.max(1, cores - 1))))));
            job.never = never.clone();
            job.start();
            send(ex, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            send(ex, 400, "application/json; charset=utf-8", "{\"error\":" + Report.q(String.valueOf(e.getMessage())) + "}");
        }
    }

    private static synchronized String status() {
        if (series == null) return Report.status(null, scores, null, seriesError, null, null, null, "idle", 0, seconds, null, never);
        Evaluator.Result base = Evaluator.evaluate(series.params, series.original, true);
        int[] g = job != null ? job.bestGrid : series.original;
        Evaluator.Result best = Evaluator.evaluate(series.params, g, true);
        String state = job == null ? "idle" : job.state.name().toLowerCase();
        double el = job == null ? 0 : job.elapsedSeconds();
        return Report.status(series, scores, job, seriesError, base, best, g, state, el, seconds, seriesText, never);
    }
}
