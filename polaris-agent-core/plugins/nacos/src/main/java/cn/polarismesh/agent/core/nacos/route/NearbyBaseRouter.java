package cn.polarismesh.agent.core.nacos.route;

import cn.polarismesh.agent.core.nacos.constants.NacosConstants;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;

/**
 * 就近路由配置.
 *
 * @author bruceppeng
 * @date 2022/11/22 11:11
 */
public class NearbyBaseRouter {

    static NearbyBaseRouter nearbyBaseRouter = new NearbyBaseRouter();
    //就近路由是否开启
    boolean enable;

    //就近路由的匹配级别集合
    String[] matchLevels;

    //如果为同云内就近路由，其云标签
    String matchLevelCloudLabel;

    //存储匹配规格，key: MatchLevel字符串形式，value: 表示优先级，数字越小，优先级越高。
    Map<String, Integer> matchLevelMap = Maps.newHashMap();

    boolean matchLevelCloud;

    public static NearbyBaseRouter getRouter() {
        return nearbyBaseRouter;
    }

    public boolean isEnable() {
        return enable;
    }

    public String[] getMatchLevels() {
        return matchLevels;
    }

    public String getMatchLevelCloudLabel() {
        return matchLevelCloudLabel;
    }

    public Map<String, Integer> getMatchLevelMap() {
        return matchLevelMap;
    }

    public boolean isMatchLevelCloud() {
        return matchLevelCloud;
    }

    public void init() {
        this.enable = Boolean.getBoolean(NacosConstants.NEARBY_BASED_ROUTER_ENABLE);

        if (!enable) {
            return;
        }
        String routerMatchLevelsStr = System.getProperty(NacosConstants.ROUTER_MATCH_LEVELS);

        if (this.enable) {
            Objects.requireNonNull(routerMatchLevelsStr, "router.match.levels is null");
        }
        this.matchLevels = routerMatchLevelsStr.split(",");

        int index = 1;
        for (String level : this.matchLevels) {
            matchLevelMap.put(level.toUpperCase(), index++);
        }
        this.matchLevelCloud = Objects.nonNull(matchLevelMap.get(MatchLevel.CLOUD.name()));
        if (this.matchLevelCloud) {
            String cloudLabel = System.getProperty(NacosConstants.ROUTER_MATCH_LEVEL_CLOUD_LABEL);
            this.matchLevelCloudLabel = cloudLabel;
            Objects.requireNonNull(cloudLabel, "router.match.level.cloud.label is null");
        }
    }
}


