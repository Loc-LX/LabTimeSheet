package com.lab.labtimesheet.feature.integration.model.dto;

/**
 * AES-GCM output persisted for an integration credential; arrays are defensively copied at every boundary.
 *
 * @param ciphertext encrypted credential including the authentication tag
 * @param nonce unique 96-bit nonce used for this encryption
 * @param keyVersion key-rotation identifier
 */
public record EncryptedSecret(byte[] ciphertext, byte[] nonce, int keyVersion) {
    /** Defensively copies both byte arrays before this value can cross the encryption boundary. */
    public EncryptedSecret {
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    /** @return a defensive copy of the encrypted credential bytes */
    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    /** @return a defensive copy of the AES-GCM nonce */
    @Override
    public byte[] nonce() {
        return nonce.clone();
    }
}
