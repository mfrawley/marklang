package com.miniml.expr;

import com.miniml.Environment;
import com.miniml.Pattern;
import com.miniml.Result;
import com.miniml.Unit;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public record Match(Expr scrutinee, List<MatchCase> cases) implements Expr {
    
    public record MatchCase(Pattern pattern, Expr body) {}
    
    @Override
    public Object eval(Environment env) {
        Object scrutineeValue = scrutinee.eval(env);
        
        for (MatchCase matchCase : cases) {
            Map<String, Object> bindings = new HashMap<>();
            if (matchPattern(matchCase.pattern(), scrutineeValue, bindings)) {
                Environment newEnv = env.extend();
                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                    newEnv.define(entry.getKey(), entry.getValue());
                }
                return matchCase.body().eval(newEnv);
            }
        }
        
        throw new RuntimeException("Non-exhaustive pattern match");
    }
    
    private boolean matchPattern(Pattern pattern, Object value, Map<String, Object> bindings) {
        return switch (pattern) {
            case Pattern.Wildcard() -> true;
            
            case Pattern.Var(String name) -> {
                bindings.put(name, value);
                yield true;
            }
            
            case Pattern.IntLit(int expected) -> 
                value instanceof Integer i && i == expected;
            
            case Pattern.BoolLit(boolean expected) ->
                value instanceof Boolean b && b == expected;
            
            case Pattern.StringLit(String expected) ->
                value instanceof String s && s.equals(expected);
            
            case Pattern.Nil() ->
                value instanceof List<?> list && list.isEmpty();
            
            case Pattern.Cons(Pattern head, Pattern tail) -> {
                if (!(value instanceof List<?> list) || list.isEmpty()) {
                    yield false;
                }
                Object headValue = list.get(0);
                List<?> tailValue = list.subList(1, list.size());
                yield matchPattern(head, headValue, bindings) &&
                      matchPattern(tail, tailValue, bindings);
            }
            
            case Pattern.Constructor(String name, java.util.Optional<Pattern> arg) -> {
                if (value instanceof Result<?, ?> result) {
                    if (name.equals("Ok") && result instanceof Result.Ok<?, ?> ok) {
                        if (arg.isPresent()) {
                            yield matchPattern(arg.get(), ok.value(), bindings);
                        }
                        yield true;
                    } else if (name.equals("Error") && result instanceof Result.Error<?, ?> error) {
                        if (arg.isPresent()) {
                            yield matchPattern(arg.get(), error.error(), bindings);
                        }
                        yield true;
                    }
                }
                yield false;
            }
        };
    }
}
