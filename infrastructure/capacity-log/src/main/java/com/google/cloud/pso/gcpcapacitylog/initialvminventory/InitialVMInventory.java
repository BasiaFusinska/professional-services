/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pso.gcpcapacitylog.initialvminventory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.compute.model.Instance;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.pso.gcpcapacitylog.services.BQHelper;
import com.google.cloud.pso.gcpcapacitylog.services.EmptyRowCollection;
import com.google.cloud.pso.gcpcapacitylog.services.GCEHelper;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class InitialVMInventory {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * This method scans a org for VMs and uploads an inventory of the current VMs for the table specificed in the input arguments.
   *
   * @param orgNumber the org number. Example: 143823328417
   * @param dataset the name of the dataset where the inventory should be written. Example: gce_capacity_log
   * @param tableName the table name where the inventory should be written. Example: initial_vm_inventory
   * @see InitialInstanceInventoryRow is the BigQuery datamodel
   */
  public static void writeVMInventorytoBQ(String projectId, String orgNumber, String dataset,
      String tableName)
      throws IOException, GeneralSecurityException, InterruptedException {

    BQHelper.deleteTable(projectId, dataset, tableName);

    List<Project> projects = GCEHelper.getProjectsForOrg(orgNumber);

    for (int i = 0; i < projects.size(); i++) {
      try {
        logger.atInfo().log(
            "Processing project (" + (i + 1) + "/" + projects.size() + ") " + projects.get(i)
                .getProjectId());

        List<Object> rows = new ArrayList<>();
        for (Instance instance : GCEHelper.getInstancesForProject(projects.get(i))) {
          rows.add(convertToBQRow(instance));
        }

        JobStatistics statistics = null;
        try {
          statistics = BQHelper
              .insertIntoTable(projectId, dataset, tableName,
                  InitialInstanceInventoryRow.getBQSchema(), rows);
          logger.atInfo().log(statistics.toString());
        } catch (EmptyRowCollection e) {
          logger.atFinest().log("No input data supplied", e);
        }


      } catch (GoogleJsonResponseException e) {
        if (e.getStatusCode() == 403) {
          logger.atFiner().log(
              "GCE API not activated for project: " + projects.get(i).getProjectId()
                  + ". Ignoring project.");
        } else {
          throw e;
        }
      }
    }
  }

  protected static InitialInstanceInventoryRow convertToBQRow(Instance instance) {
    return new InitialInstanceInventoryRow(instance.getCreationTimestamp(),
        instance.getId().toString(),
        instance.getZone(),
        instance.getMachineType(), instance.getScheduling().getPreemptible(), instance.getTags(), instance.getLabels());
  }
}