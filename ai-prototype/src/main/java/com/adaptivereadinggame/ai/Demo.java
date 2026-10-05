package com.adaptivereadinggame.ai;

import com.google.gson.GsonBuilder;
import java.net.URI;
import java.util.List;
import static com.adaptivereadinggame.ai.DifficultyService.*;

/** Console demonstration of real Java -> Python -> model -> Java interaction. */
public final class Demo {
    public static void main(String[] args) {
        var service = new DifficultyService(new QwenClient(URI.create(args.length == 0
                ? "http://127.0.0.1:8765/infer" : args[0])));
        String[] names = {"strong", "moderate", "struggling", "improving"};
        int[][] cases = {{4,4,5}, {3,3,3}, {2,1,1}, {3,4,5}};
        for (int i = 0; i < cases.length; i++) {
            var history = java.util.Arrays.stream(cases[i]).mapToObj(n -> new Section(Level.MEDIUM, n, 5)).toList();
            Decision result = service.decide(Level.MEDIUM, history);
            System.out.println(names[i] + ": " + new GsonBuilder().create().toJson(result));
            if (Boolean.getBoolean("requireModel") && !result.source().equals("qwen"))
                throw new IllegalStateException("Verification requires actual Qwen, not fallback");
        }
        System.out.println("cold_start: " + service.decide(Level.MEDIUM, List.of()).source());
    }
}
