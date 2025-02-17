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

package cn.polarismesh.agent.core.asm.registry;

import java.util.concurrent.atomic.AtomicReferenceArray;

public final class WeakAtomicReferenceArray<T> {

    private final int length;
    private final AtomicReferenceArray<T> atomicArray;

    public WeakAtomicReferenceArray(int length, Class<? extends T> clazz) {
        this.length = length;
        this.atomicArray = new AtomicReferenceArray<T>(length);
    }

    public void set(int index, T newValue) {
        this.atomicArray.set(index, newValue);
    }

    public int length() {
        return length;
    }


    public T get(int index) {
        // thread safe read
        return this.atomicArray.get(index);
    }

}
