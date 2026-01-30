fn abs (x: double) = if x < 0.0 then 0.0 - x else x;

fn max (a: double) (b: double) = if a > b then a else b;

fn min (a: double) (b: double) = if a < b then a else b;

fn square (x: double) = x * x;

fn sqrt (x: double) = java_call "java.lang.Math" "sqrt" x;

fn sin (x: double) = java_call "java.lang.Math" "sin" x;

fn cos (x: double) = java_call "java.lang.Math" "cos" x;

fn tan (x: double) = java_call "java.lang.Math" "tan" x;

fn log (x: double) = java_call "java.lang.Math" "log" x;

fn exp (x: double) = java_call "java.lang.Math" "exp" x;
