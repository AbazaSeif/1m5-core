package io.onemfive.core.contract.omni;

import io.onemfive.core.contract.Contract;
import io.onemfive.data.Envelope;

import java.util.logging.Logger;

/**
 * TODO: Add Description
 *
 * http://www.omnilayer.org/
 * https://www.omniwallet.org
 *
 * @author objectorange
 */
public class OmniContract implements Contract {

    private static final Logger LOG = Logger.getLogger(OmniContract.class.getName());

    @Override
    public void createContract(Envelope envelope) {

    }

    @Override
    public void sendCurrencyToContract(Envelope envelope) {

    }

    @Override
    public void addVoter(Envelope envelope) {

    }

    @Override
    public void removeVoter(Envelope envelope) {

    }

    @Override
    public void killContract(Envelope envelope) {

    }
}
