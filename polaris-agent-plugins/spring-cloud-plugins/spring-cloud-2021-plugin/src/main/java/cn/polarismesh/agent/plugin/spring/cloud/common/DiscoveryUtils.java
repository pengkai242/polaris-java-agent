/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.agent.plugin.spring.cloud.common;

import com.tencent.cloud.polaris.discovery.PolarisDiscoveryHandler;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DiscoveryUtils {

	public static PolarisDiscoveryHandler buildDiscoveryHandler() {
		return new PolarisDiscoveryHandler(
				Holder.getDiscoveryProperties(),
				PolarisOperator.getInstance().getProviderAPI(),
				PolarisOperator.getInstance().getSdkContext(),
				PolarisOperator.getInstance().getConsumerAPI()
		);
	}

}
