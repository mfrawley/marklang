package com.miniml;

public sealed interface Result<T, E> {
    record Ok<T, E>(T value) implements Result<T, E> {
        @Override
        public String toString() {
            return "Ok(" + value + ")";
        }
    }
    
    record Error<T, E>(E error) implements Result<T, E> {
        @Override
        public String toString() {
            return "Error(" + error + ")";
        }
    }
    
    default boolean isOk() {
        return this instanceof Ok;
    }
    
    default boolean isError() {
        return this instanceof Error;
    }
}
