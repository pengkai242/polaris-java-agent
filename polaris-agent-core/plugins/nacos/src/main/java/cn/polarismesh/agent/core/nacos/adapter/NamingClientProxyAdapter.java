package cn.polarismesh.agent.core.nacos.adapter;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

import cn.polarismesh.agent.core.nacos.constants.NacosConstants;
import cn.polarismesh.agent.core.nacos.route.NearbyBaseRouter;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.NamingClientProxyDelegate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * 自定义 NamingClientProxyAdapter.
 *
 * @author bruceppeng
 */
public class NamingClientProxyAdapter implements NamingClientProxy {

    private NamingClientProxy clientProxy;

    private NamingClientProxy targetClientProxy;

    private String targetNacosDomain;

    private NearbyBaseRouter nearbyBaseRouter;

    public NamingClientProxyAdapter(String namespace, ServiceInfoHolder serviceInfoHolder, Properties properties,
            InstancesChangeNotifier changeNotifier) throws NacosException {
        this.clientProxy = new NamingClientProxyDelegate(namespace, serviceInfoHolder, properties, changeNotifier);
        targetNacosDomain = System.getProperty(NacosConstants.TARGET_NACOS_SERVER_ADDR);

        this.nearbyBaseRouter = NearbyBaseRouter.getRouter();
        this.nearbyBaseRouter.init();

        //组装target nacos的properties配置信息
        Properties targetProperties = new Properties();
        targetProperties.putAll(properties);
        targetProperties.setProperty(PropertyKeyConst.SERVER_ADDR, targetNacosDomain);
        this.targetClientProxy = new NamingClientProxyDelegate(namespace, serviceInfoHolder, targetProperties, changeNotifier);

    }

    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        fillMetadata(instance);
        clientProxy.registerService(serviceName, groupName, instance);
        targetClientProxy.registerService(serviceName, groupName, instance);
    }

    /**
     * fillMetadata 补充元数据信息.
     *
     * @param instance
     */
    private void fillMetadata(Instance instance) {

        // 针对服务注册做特殊处理，如果开启根据路由标签优先访问，则增加metadata数据：router.match.level.cloud.label
        if (nearbyBaseRouter.isEnable() && nearbyBaseRouter.isMatchLevelCloud()) {
            instance.addMetadata(NacosConstants.ROUTER_MATCH_LEVEL_CLOUD_LABEL, nearbyBaseRouter.getMatchLevelCloudLabel());
        }
    }

    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        clientProxy.deregisterService(serviceName, groupName, instance);
        targetClientProxy.deregisterService(serviceName, groupName, instance);
    }

    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {

    }

    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
            boolean healthyOnly) throws NacosException {
        ServiceInfo serviceInfo = clientProxy.queryInstancesOfService(serviceName, groupName, clusters, udpPort, healthyOnly);
        ServiceInfo targetServiceInfo = targetClientProxy.queryInstancesOfService(serviceName, groupName, clusters, udpPort, healthyOnly);

        return mergeInstances(serviceInfo, targetServiceInfo);
    }

    /**
     * 合并两个nacos server的实例列表
     * @param serviceInfo
     * @param secondServiceInfo
     */
    private ServiceInfo mergeInstances(ServiceInfo serviceInfo, ServiceInfo secondServiceInfo) {

        if (secondServiceInfo == null) {
            return serviceInfo;
        }

        if (serviceInfo == null) {
            return secondServiceInfo;
        }

        try {
            List<Instance> hosts =  serviceInfo.getHosts();
            List<Instance> secondHosts =  secondServiceInfo.getHosts();

            Map<String, Instance> hostMap = new HashMap<String, Instance>(hosts.size());
            for (Instance host : hosts) {
                hostMap.put(host.toInetAddr(), host);
            }

            for (Instance host : secondHosts) {
                String inetAddr = host.toInetAddr();
                if (hostMap.get(inetAddr) == null) {
                    hosts.add(host);
                }
            }
            // 对hosts进行过滤
            List<Instance> finalHosts = filterInstances(hosts);
            serviceInfo.setHosts(finalHosts);
        }catch(Exception exp){
            NAMING_LOGGER.error("NamingClientProxyAdapter mergeInstances request {} failed.", targetNacosDomain, exp);
        }
        return serviceInfo;

    }

    /**
     * filterInstances 对实例列表进行过滤筛选.
     *
     * @param hosts
     * @return
     */
    private List<Instance> filterInstances(List<Instance> hosts) {

        // 针对服务实例做特殊处理，如果开启同nacos集群优先，则优先返回同nacos集群的实例
        if (!nearbyBaseRouter.isEnable()) {
            return hosts;
        }
        List<Instance> finalHosts = Lists.newArrayList();
        for (Instance instance : hosts) {
            String matchLevelCloudLabel = Optional.ofNullable(instance.getMetadata()).orElse(Maps.newHashMap()).get(NacosConstants.ROUTER_MATCH_LEVEL_CLOUD_LABEL);
            if (this.nearbyBaseRouter.getMatchLevelCloudLabel().equals(matchLevelCloudLabel)) {
                finalHosts.add(instance);
            }
        }
        if (finalHosts.isEmpty()) {
            return hosts;
        }
        return finalHosts;
    }

    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        return null;
    }

    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {

    }

    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        return false;
    }

    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {

    }

    @Override
    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        return clientProxy.getServiceList(pageNo, pageSize, groupName, selector);
    }


    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        ServiceInfo serviceInfo = clientProxy.subscribe(serviceName, groupName, clusters);
        ServiceInfo targetServiceInfo = targetClientProxy.subscribe(serviceName, groupName, clusters);
        return mergeInstances(serviceInfo, targetServiceInfo);
    }

    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        clientProxy.unsubscribe(serviceName, groupName, clusters);
        targetClientProxy.unsubscribe(serviceName, groupName, clusters);
    }

    @Override
    public boolean isSubscribed(String serviceName, String groupName, String clusters) throws NacosException {
        return clientProxy.isSubscribed(serviceName, groupName, clusters) || targetClientProxy.isSubscribed(serviceName, groupName, clusters) ;
    }

    @Override
    public void updateBeatInfo(Set<Instance> modifiedInstances) {
        clientProxy.updateBeatInfo(modifiedInstances);
        targetClientProxy.updateBeatInfo(modifiedInstances);
    }

    @Override
    public boolean serverHealthy() {
        return clientProxy.serverHealthy();
    }

    @Override
    public void shutdown() throws NacosException {
        clientProxy.shutdown();
        targetClientProxy.shutdown();
    }
}