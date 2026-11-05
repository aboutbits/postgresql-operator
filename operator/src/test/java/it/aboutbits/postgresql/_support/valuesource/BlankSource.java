package it.aboutbits.postgresql._support.valuesource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.params.provider.ValueSource;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ValueSource(strings = {"", " ", "         ", "\t", "\r", "\n",
            "\r\n", "\f", "\u000B"})
@NullMarked
public @interface BlankSource {}
