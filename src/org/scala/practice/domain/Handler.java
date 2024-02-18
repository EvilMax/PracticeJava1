package org.scala.practice.domain;

public interface Handler {
    ApplicationStatusResponse performOperation(String id);
}
