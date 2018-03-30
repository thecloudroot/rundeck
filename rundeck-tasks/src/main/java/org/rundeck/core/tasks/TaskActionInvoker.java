/*
 * Copyright 2018 Rundeck, Inc. (http://rundeck.com)
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
 */

package org.rundeck.core.tasks;

import java.util.Map;

/**
 * Provides a callback when a trigger is fired by the TaskTriggerHandler
 */
public interface TaskActionInvoker<T extends TaskContext> {
    /**
     * The condition was met for a registered trigger
     *
     * @param triggerMap any additional data from the trigger
     */
    void taskTriggerFired(T contextInfo, Map triggerMap);
}