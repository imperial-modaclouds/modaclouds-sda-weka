/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
 *  Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://http://www.gnu.org/licenses/gpl.html
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.sda.weka;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import imperial.modaclouds.monitoring.data_retriever.Client_Server;
import imperial.modaclouds.monitoring.sda.basic.ConfigurationException;
import imperial.modaclouds.monitoring.sda.basic.DataCollectorAgent;
import it.polimi.modaclouds.monitoring.dcfactory.DCConfig;
import it.polimi.modaclouds.monitoring.kb.api.DeserializationException;
import it.polimi.modaclouds.qos_models.monitoring_ontology.VM;

public class WekaSDA {

	/**
	 * The unique monitored resource ID.
	 */
	private static String monitoredResourceID;

	private static DataCollectorAgent dcAgent;

	private static ArrayList<String> supportedFunctions;

	/**
	 * @param args
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {	

		initialize();

		try {
			DataCollectorAgent.initialize();
		} catch (ConfigurationException e1) {
			e1.printStackTrace();
		}
		DataCollectorAgent.getInstance().startSyncingWithKB();

		try {
			Client_Server.retrieve(Integer.valueOf(args[0]));
		} catch (Exception e) {
			e.printStackTrace();
		}

		dcAgent = DataCollectorAgent.getInstance();

		ArrayList<Map<String, String>> parameters = new ArrayList<Map<String, String>>();
		ArrayList<String> returnedMetric = new ArrayList<String>();
		ArrayList<String> targetMetric = new ArrayList<String>();
		ArrayList<ArrayList<String>> targetResources = new ArrayList<ArrayList<String>>();
		ArrayList<String> type = new ArrayList<String>();
		ArrayList<Integer> period = new ArrayList<Integer>();
		ArrayList<Integer> nextPauseTime = new ArrayList<Integer>();

		long startTime = 0;

		while(true) {

			if (System.currentTimeMillis() - startTime > 60000) {

				Collection<DCConfig> dcConfig = dcAgent.getConfiguration(DataCollectorAgent.getVmId(),null);

				System.out.println(dcConfig.size());

				for (DCConfig dc : dcConfig) {

					if (supportedFunctions.contains(dc.getMonitoredMetric())) {
						parameters.add(dc.getParameters());
						returnedMetric.add(dc.getMonitoredMetric());
						type.add(dc.getMonitoredMetric());
					}

					if (dc.getParameters().get("samplingTime") != null) {
						period.add(Integer.valueOf(dc.getParameters().get("samplingTime"))*1000);
						nextPauseTime.add(Integer.valueOf(dc.getParameters().get("samplingTime"))*1000);
					}

					Set<String> resourceType = dc.getMonitoredResourcesTypes();
					Iterator<String> itResourceType = resourceType.iterator();
					ArrayList<String> vms = new ArrayList<String>();
					while (itResourceType.hasNext()) {
						Set<VM> vmTypes = null;
						try {
							vmTypes = (Set<VM>)dcAgent.getEntitiesByPropertyValue(itResourceType.next(), "type", "model");
						} catch (DeserializationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						Iterator<VM> itVM = vmTypes.iterator();

						while (itVM.hasNext()) {
							vms.add(itVM.next().getId());
						}
					}
					targetResources.add(vms);

				}

				if (type.size() == 0) {
					try {
						System.out.println("No weka SDA function received.");
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					continue;
				}

				startTime = System.currentTimeMillis();
			}

			int toSleep = Collections.min(nextPauseTime);
			int index = nextPauseTime.indexOf(toSleep);

			try {
				Thread.sleep(Math.max(toSleep, 0));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			double value = -1;

			for (int i = 0; i < targetResources.get(index).size(); i++) {
				switch(type.get(index)) {
				case "ForecastingML":
					TimeSeriesForecasting forecasting = new TimeSeriesForecasting();
					value = forecasting.forecast(targetResources.get(index).get(i), targetMetric.get(index), parameters.get(index));
					break;
				case "Correlation":
					Correlation correlation = new Correlation();
					value = correlation.correlate(targetResources.get(index).get(i), targetMetric.get(index), parameters.get(index));
					break;
				}
				if (Math.abs(value + 1) >= 0.00001) {
					System.out.println("value: "+value);
					try {
						dcAgent.sendSyncMonitoringDatum(String.valueOf(value), returnedMetric.get(index), targetResources.get(index).get(i));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}


		}
	}

	private static void initialize() {
		supportedFunctions = new ArrayList<String>();
		supportedFunctions.add("ForecastingML");
		supportedFunctions.add("Correlation");
	}

}
