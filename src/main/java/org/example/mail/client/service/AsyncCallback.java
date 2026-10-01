package org.example.mail.client.service;

@FunctionalInterface
public interface AsyncCallback<T> {
    void onComplete(T result, Exception error);
}
