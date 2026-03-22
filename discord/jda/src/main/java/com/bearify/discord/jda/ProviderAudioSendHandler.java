package com.bearify.discord.jda;

import com.bearify.discord.api.voice.AudioProvider;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Adapts an {@link AudioProvider} to JDA's {@link AudioSendHandler}, and implements
 * {@link Closeable} so that {@link JdaVoiceSession#leave()} can release the provider's resources.
 */
class ProviderAudioSendHandler implements AudioSendHandler, Closeable {

    private final AudioProvider provider;

    ProviderAudioSendHandler(AudioProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean canProvide() {
        return provider.canProvide();
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(provider.provide20MsAudio());
    }

    @Override
    public boolean isOpus() {
        return provider.isOpus();
    }

    @Override
    public void close() throws IOException {
        provider.close();
    }
}
