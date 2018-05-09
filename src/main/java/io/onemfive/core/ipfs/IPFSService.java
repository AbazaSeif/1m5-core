package io.onemfive.core.ipfs;

import io.onemfive.core.BaseService;
import io.onemfive.core.MessageProducer;

import java.util.Properties;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class IPFSService extends BaseService {

    public IPFSService(MessageProducer producer) {
        super(producer);
    }

    @Override
    public boolean start(Properties properties) {
        System.out.println(IPFSService.class.getSimpleName()+": starting...");

        System.out.println(IPFSService.class.getSimpleName()+": started.");
        return true;
    }

}