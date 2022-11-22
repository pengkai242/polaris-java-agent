package cn.polarismesh.agent.core.nacos.route;

/**
 * 就近路由匹配级别.
 * @author bruceppeng
 * @date 2022/11/22 15:15
 */

enum MatchLevel {

    ZONE,//按可用区就近路由
    REGION,//按区域就近路由
    CLOUD,//按同云就近路由
}