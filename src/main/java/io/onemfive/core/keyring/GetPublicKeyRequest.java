package io.onemfive.core.keyring;

import io.onemfive.core.ServiceRequest;

public class GetPublicKeyRequest extends ServiceRequest {
    public String alias;
    public String hash;
}