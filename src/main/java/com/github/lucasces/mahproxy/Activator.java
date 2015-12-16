package com.github.lucasces.mahproxy;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {
    Thread proxyServerThread = null;
    ProxyServer proxyServer = null;
    @Override
    public void start(BundleContext context){
        proxyServer = new ProxyServer();
        proxyServerThread = new Thread(proxyServer);
        proxyServerThread.start();
    }

    @Override
    public void stop(BundleContext context){
        proxyServer.stop();
        proxyServerThread.stop();
    }
}
