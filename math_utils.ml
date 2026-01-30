# Math utilities module
fn abs x =
  java_call "java.lang.Math" "abs" x

fn max a b =
  java_call "java.lang.Math" "max" a b

fn min a b =
  java_call "java.lang.Math" "min" a b

0
