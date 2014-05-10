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

import imperial.modaclouds.monitoring.data_retriever.Client_Server;
import imperial.modaclouds.monitoring.data_retriever.ValueSet;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import weka.core.Instances;
import weka.classifiers.functions.GaussianProcesses;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.timeseries.WekaForecaster;

public class TimeSeriesForecasting {
	
	public double forecast(String targetResource, String targetMetric, Set<Parameter> parameters) {
		
		ArrayList<ArrayList<String>> timestamps = new ArrayList<ArrayList<String>>();
		ArrayList<ArrayList<String>> data = new ArrayList<ArrayList<String>>();
		
		ValueSet targetData = Client_Server.obtainData(targetResource, targetMetric);
		
		if (targetData == null) {
			System.out.println("No data received");
			return -1;
		}
		
		timestamps.add(targetData.getTimestamps());
		data.add(targetData.getValues());
		
		ArrayList<String> metricName = new ArrayList<String>();
		metricName.add("time");
		metricName.add(targetResource+targetMetric);
		
		int predictionStep = 0;
		String otherTarget1 = null;
		String otherTarget2 = null;
		String otherMetric1 = null;
		String otherMetric2 = null;
		String method = null;
		
		for (Parameter par: parameters) {
			switch (par.getName()) {
			case "predictionStep":
				predictionStep = Integer.valueOf(par.getValue());
				break;
			case "otherTarget1":
				otherTarget1 = par.getValue();
				break;
			case "otherTarget2":
				otherTarget2 = par.getValue();
				break;
			case "otherMetric1":
				otherMetric1 = par.getValue();
				break;
			case "otherMetric2":
				otherMetric2 = par.getValue();
				break;
			case "method":
				method = par.getValue();
				break;
			}
		}
		
		if (otherTarget1 != null) {
			ValueSet temp = Client_Server.obtainData(otherTarget1, otherMetric1);
			timestamps.add(temp.getTimestamps());
			data.add(temp.getValues());
			metricName.add(otherTarget1+otherMetric1);
		}
		
		if (otherTarget2 != null) {
			ValueSet temp = Client_Server.obtainData(otherTarget2, otherMetric2);
			timestamps.add(temp.getTimestamps());
			data.add(temp.getValues());
			metricName.add(otherTarget2+otherMetric2);
		}
		
		String fileName = metricName.get(1)+"Forecasting.arff";
		CreateArff.create(timestamps, data, metricName, fileName);
		
		double[][] value = compute(fileName, method, metricName.get(1), predictionStep);

		return value[predictionStep-1][0];
	}

	/**
	 * This method calculates the target metrics using correlation algorithm with the weka library.
	 * 
	 * @param arffFile	the arff file for weka
	 * @param method	the time series forecasting algorithm for weka
	 * @param target	the target metric
	 * @param predictionStep_str	the prediction step in future
	 * @return	the value of the target metric
	 */
	public double[][] compute(String arffFile, String method, String target, int predictionStep) {

		double [][] result = null;

		int nbTargets = StringUtils.countMatches(target, ",") + 1;  


		try {

			Instances data = new Instances(new BufferedReader(new FileReader(arffFile)));

			WekaForecaster forecaster = new WekaForecaster();

			forecaster.setFieldsToForecast(target);

			switch(method) {
			case "LinearRegression":
				forecaster.setBaseForecaster(new LinearRegression());
				break;
			case "GaussianProcess":
				forecaster.setBaseForecaster(new GaussianProcesses());
				break;
			case "SMOreg":
				forecaster.setBaseForecaster(new SMOreg());
				break;
			}

			forecaster.buildForecaster(data, System.out);

			forecaster.primeForecaster(data);

			List<List<NumericPrediction>> forecast = forecaster.forecast(predictionStep, System.out);

			result = new double[predictionStep][nbTargets];
			
			for (int i = 0; i < predictionStep; i++) {
				List<NumericPrediction> predsAtStep = forecast.get(i);
				for (int j = 0; j < nbTargets; j++) {
					NumericPrediction predForTarget = predsAtStep.get(j);
					result[i][j] = predForTarget.predicted();
					//System.out.print("" + predForTarget.predicted() + " ");
				}
				//System.out.println();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return result;
	}
}
