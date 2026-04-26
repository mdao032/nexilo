package com.nexilo.infra.queue;

import org.springframework.context.ApplicationEvent;

/**
 * Événement Spring publié lorsqu'un nouveau job IA est soumis.
 *
 * <p>Utilisé par {@link AiJobProcessor} pour déclencher le traitement asynchrone
 * sans couplage direct entre {@link AiJobServiceImpl} et le processeur.
 */
public class AiJobEvent extends ApplicationEvent {

    private final AiJob job;

    public AiJobEvent(Object source, AiJob job) {
        super(source);
        this.job = job;
    }

    public AiJob getJob() {
        return job;
    }
}

