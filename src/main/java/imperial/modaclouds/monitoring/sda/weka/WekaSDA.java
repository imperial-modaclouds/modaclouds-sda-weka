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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import imperial.modaclouds.monitoring.data_retriever.Client_Server;
import imperial.modaclouds.monitoring.sda.basic.Config;
import imperial.modaclouds.monitoring.sda.basic.ConfigurationException;
import it.polimi.tower4clouds.common.net.UnexpectedAnswerFromServerException;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.manager.api.ManagerAPI;
import it.polimi.tower4clouds.model.data_collectors.DCDescriptor;
import it.polimi.tower4clouds.model.ontology.Resource;

public class WekaSDA {

	/**
	 * @param args
	 */
	public static void main(String[] args) {	

		Config config = null;
		try {
			config = Config.getInstance();
		} catch (ConfigurationException e2) {
			e2.printStackTrace();
		}
		ManagerAPI manager = new ManagerAPI(config.getMmIP(),
				config.getMmPort());

		DCAgent dcAgent = new DCAgent(manager);
		DCDescriptor dcDescriptor = new DCDescriptor();
		dcDescriptor.setConfigSyncPeriod(60);
		dcAgent.setDCDescriptor(dcDescriptor);
		dcAgent.start();

		String port = System.getenv("MODACLOUDS_JAVA_SDA_PORT");
		if (port == null){
			System.out.println("MODACLOUDS_JAVA_SDA_PORT is not found");
			System.exit(-1);
		}

		try {
			Client_Server.retrieve(Integer.parseInt(port));
		} catch (Exception e) {
			e.printStackTrace();
		}

		ArrayList<Map<String, String>> parameters = new ArrayList<Map<String, String>>();
		ArrayList<String> returnedMetric = new ArrayList<String>();
		ArrayList<String> targetMetric = new ArrayList<String>();
		ArrayList<String> type = new ArrayList<String>();
		ArrayList<Integer> period = new ArrayList<Integer>();
		ArrayList<Integer> nextPauseTime = new ArrayList<Integer>();

		long startTime = 0;

		while(true) {

			if (System.currentTimeMillis() - startTime > 60000) {

				Set<String> requiredMetrics = null;
				try {
					requiredMetrics = manager.getRequiredMetrics();
				} catch (UnexpectedAnswerFromServerException | IOException e1) {
					e1.printStackTrace();
				}

				for (String requiredMetric : requiredMetrics) {
					if (requiredMetric.startsWith("ForecastingML") || requiredMetric.startsWith("Correlation")) {
						dcDescriptor.addMonitoredResource(requiredMetric,
								new Resource()); 
						dcAgent.refresh();

						int index = requiredMetric.indexOf("_");
						String metricToBeForecast = requiredMetric.substring(index+1);
						System.out.println("Forecast required for metric "
								+ metricToBeForecast);
						try {
							manager.registerHttpObserver(metricToBeForecast, config.getSdaURL(),
									"TOWER/JSON");
						} catch (UnexpectedAnswerFromServerException
								| IOException e) {
							e.printStackTrace();
						}

						parameters.add(dcAgent.getParameters(requiredMetric));

						if (dcAgent.getParameters(requiredMetric).get("samplingTime") != null) {
							period.add(Integer.valueOf(dcAgent.getParameters(requiredMetric).get("samplingTime"))*1000);
							nextPauseTime.add(Integer.valueOf(dcAgent.getParameters(requiredMetric).get("samplingTime"))*1000);
						}
						targetMetric.add(metricToBeForecast);
						returnedMetric.add(requiredMetric);
						type.add(requiredMetric.substring(0, index));
					}
				}


				if (type.size() == 0) {
					try {
						System.out.println("No weka SDA function received.");
						Thread.sleep(10000);
					} catch (InterruptedException e) {
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

			ArrayList<String> targetResources = new ArrayList<String>();
			targetResources = Client_Server.getMetricMap().get(targetMetric.get(index));

			if (targetResources == null) {
				System.out.println("No resources found for metric: "+targetMetric.get(index));
			}
			else
				for (int i = 0; i < targetResources.size(); i++) {
					switch(type.get(index)) {
					case "ForecastingML":
						TimeSeriesForecasting forecasting = new TimeSeriesForecasting();
						value = forecasting.forecast(targetResources.get(i), targetMetric.get(index), parameters.get(index));
						break;
					case "Correlation":
						Correlation correlation = new Correlation();
						value = correlation.correlate(targetResources.get(i), targetMetric.get(index), parameters.get(index));
						break;
					}
					if (Math.abs(value + 1) >= 0.00001) {
						System.out.println("value: "+value);
						try {
							dcAgent.send(new Resource(null, targetResources.get(i)), returnedMetric.get(index), value);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
		}

	}
}


