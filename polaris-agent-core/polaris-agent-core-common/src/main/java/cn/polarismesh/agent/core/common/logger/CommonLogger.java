/*
 * Copyright 2016 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package cn.polarismesh.agent.core.common.logger;

public interface CommonLogger {

    boolean isTraceEnabled();

    void trace(String msg);

    boolean isDebugEnabled();

    void debug(String msg);

    boolean isInfoEnabled();

    void info(String msg);

    boolean isWarnEnabled();

    void warn(String msg);

    void warn(String msg, Throwable throwable);

}
