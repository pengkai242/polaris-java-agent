package cn.polarismesh.agent.core.nacos.v1.delegate;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

import cn.polarismesh.agent.core.nacos.v1.constants.NacosConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义NacosV1NamingProxy
 *
 * @author bruceppeng
 */
public class NacosV1NamingProxy extends NamingProxy {

    private int maxRetry;

    private String targetNacosDomain;

    // nacos集群的tag,用来识别是属于那个nacos集群
    private String nacosTag;

    // 用来表示是否开启同nacos访问优先
    private boolean sameNacosAccessPreferred;

    private static final Map<String, Boolean> nacosCallCache = new ConcurrentHashMap<>(256);

    public NacosV1NamingProxy(String namespaceId, String endpoint, String serverList, Properties properties) {
        super(namespaceId, endpoint, serverList, properties);

        this.maxRetry = UtilAndComs.REQUEST_DOMAIN_RETRY_COUNT;

        targetNacosDomain = System.getProperty(NacosConstants.TARGET_NACOS_SERVER_ADDR);
        nacosTag = System.getProperty(NacosConstants.NACOS_TAG);
        sameNacosAccessPreferred = Boolean.getBoolean(NacosConstants.SAME_NACOS_ACCESS_PREFERRED);
        if (sameNacosAccessPreferred) {
            Objects.requireNonNull(nacosTag, "nacosTag is null");
        }
        init();
    }

    /**
     * 初始化nacosCallCache
     */
    private void init() {
        nacosCallCache.put(NacosConstants.REGISTER_SERVICE, true);
        nacosCallCache.put(NacosConstants.DEREGISTER_SERVICE, true);
        nacosCallCache.put(NacosConstants.SEND_BEAT, true);
        nacosCallCache.put(NacosConstants.QUERY_LIST, true);
    }

    /**
     * Query instance list.
     *
     * @param serviceName service name
     * @param clusters clusters
     * @param udpPort udp port
     * @param healthyOnly healthy only
     * @return instance list
     * @throws NacosException nacos exception
     */
    @Override
    public String queryList(String serviceName, String clusters, int udpPort, boolean healthyOnly)
            throws NacosException {

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put(CommonParams.NAMESPACE_ID, getNamespaceId());
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put("clusters", clusters);
        params.put("udpPort", String.valueOf(udpPort));
        params.put("clientIP", NetUtils.localIP());
        params.put("healthyOnly", String.valueOf(healthyOnly));

        String api = UtilAndComs.nacosUrlBase + "/instance/list";

        String result = super.reqApi(api, params, HttpMethod.GET);
        if (Strings.isNullOrEmpty(targetNacosDomain)) {
            return result;
        }

        String secondResult = callServerForTarget(api, params, Collections.EMPTY_MAP, HttpMethod.GET);

        return mergeResult(result, secondResult);

    }


    /**
     * 合并两个nacos server的实例列表
     *
     * @param result
     * @param secondResult
     */
    private String mergeResult(String result, String secondResult) {

        if (StringUtils.isEmpty(secondResult)) {
            return result;
        }

        if (StringUtils.isEmpty(result)) {
            return secondResult;
        }

        try {
            ServiceInfo serviceInfo = JacksonUtils.toObj(result, ServiceInfo.class);
            ServiceInfo secondServiceInfo = JacksonUtils.toObj(secondResult, ServiceInfo.class);

            List<Instance> hosts = serviceInfo.getHosts();
            List<Instance> secondHosts = secondServiceInfo.getHosts();

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
            return JacksonUtils.toJson(serviceInfo);
        } catch (Exception exp) {
            NAMING_LOGGER.error("NacosV1NamingProxy mergeResult request {} failed.", targetNacosDomain, exp);
        }
        return result;

    }

    /**
     * filterInstances 对实例列表进行过滤筛选.
     *
     * @param hosts
     * @return
     */
    private List<Instance> filterInstances(List<Instance> hosts) {

        // 针对服务实例做特殊处理，如果开启同nacos集群优先，则优先返回同nacos集群的实例
        if (!sameNacosAccessPreferred) {
            return hosts;
        }
        List<Instance> finalHosts = Lists.newArrayList();
        for (Instance instance : hosts) {
            String nacosTag = Optional.ofNullable(instance.getMetadata()).orElse(Maps.newHashMap()).get("nacosTag");
            if (this.nacosTag.equals(nacosTag)) {
                finalHosts.add(instance);
            }
        }
        if (finalHosts.isEmpty()) {
            return hosts;
        }
        return finalHosts;
    }

    /**
     * Request api.
     *
     * @param api api
     * @param params parameters
     * @param body body
     * @param servers servers
     * @param method http method
     * @return result
     * @throws NacosException nacos exception
     */
    @Override
    public String reqApi(String api, Map<String, String> params, Map<String, String> body, List<String> servers,
            String method) throws NacosException {
        fillMetadata(api, params, method);

        String sourceResult = super.reqApi(api, params, body, servers, method);
        //处理对目的地址的请求,即使报错也不能影响原有的server调用
        callServerForTarget(api, params, body, method);
        return sourceResult;

    }

    /**
     * fillMetadata 补充元数据信息.
     *
     * @param api
     * @param params
     * @param method
     */
    private void fillMetadata(String api, Map<String, String> params, String method) {

        String fullApi = api + NacosConstants.LINK_FLAG + method;
        // 针对服务注册做特殊处理，如果开启同nacos集群优先，则增加metadata数据：nacosTag
        if (sameNacosAccessPreferred && fullApi.equals(NacosConstants.REGISTER_SERVICE)) {
            Map<String, String> metadata = JacksonUtils.toObj(params.get("metadata"), Map.class);
            metadata.put("nacosTag", nacosTag);
            params.put("metadata", JacksonUtils.toJson(metadata));
        }
    }

    /**
     * 调用目标nacos server的指定接口
     *
     * @param api
     * @param params
     * @param body
     * @param method
     * @return
     */
    private String callServerForTarget(String api, Map<String, String> params, Map<String, String> body,
            String method) {
        if (Strings.isNullOrEmpty(targetNacosDomain)) {
            return StringUtils.EMPTY;
        }
        String callName = api + NacosConstants.LINK_FLAG + method;
        Boolean bool = nacosCallCache.get(callName);
        if (bool == null || !bool) {
            return StringUtils.EMPTY;
        }
        for (int i = 0; i < maxRetry; i++) {
            try {
                //1.请求目标nacos server
                return callServer(api, params, body, targetNacosDomain, method);
            } catch (NacosException e) {
                if (NAMING_LOGGER.isDebugEnabled()) {
                    NAMING_LOGGER
                            .debug("NacosV1NamingProxy callServerForTarget request {} failed.", targetNacosDomain, e);
                }
            }
        }
        return StringUtils.EMPTY;
    }

}