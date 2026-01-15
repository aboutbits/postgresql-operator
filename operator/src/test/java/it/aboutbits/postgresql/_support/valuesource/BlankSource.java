package it.aboutbits.postgresql._support.valuesource;

import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ValueSource(strings = {"", " ", "         ", "\t", "\r", "\n", "\r\n", "\f", "\u000B"})
public @interface BlankSource {
}
