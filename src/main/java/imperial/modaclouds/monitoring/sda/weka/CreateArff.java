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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;

public class CreateArff {
	
	/**
	 * Create arff file given the data
	 * 
	 * @param timestamps_str	the timestamps data
	 * @param data	the values of the metrics
	 * @param metricName	the metric name
	 * @param fileName	the file name to keep the arff file
	 */
	public static void create(ArrayList<ArrayList<String>> timestamps_str, 
			ArrayList<ArrayList<String>> data, ArrayList<String> metricName, String fileName){
				
		long min_timestamp = Long.valueOf(Collections.min(timestamps_str.get(0)));
		long max_timestamp = Long.valueOf(Collections.max(timestamps_str.get(0)));
		
		for (int i = 1; i < timestamps_str.size(); i++) {
			long min_temp = Long.valueOf(Collections.min(timestamps_str.get(i)));
			long max_temp = Long.valueOf(Collections.max(timestamps_str.get(i)));
			
			if (max_temp < max_timestamp) {
				max_timestamp = max_temp;
			}
			
			if (min_temp > min_timestamp) {
				min_timestamp = min_temp;
			}
		}
		
		for (int i = 0; i < timestamps_str.size(); i++) {
			Iterator<String> iter_time = timestamps_str.get(i).iterator();
			Iterator<String> iter_data = data.get(i).iterator();
			
			while (iter_time.hasNext()) {
				long temp_timestamps = Long.valueOf(iter_time.next());
				if (temp_timestamps < min_timestamp || temp_timestamps > max_timestamp) {
					iter_time.remove();
					
					iter_data.next();
					iter_data.remove();
				}
			}
		}
		
		double[] timestamps = convertDoubles(timestamps_str.get(0));
		double[] targetData = convertDoubles(data.get(0));

		double[][] otherData = new double[data.size()-2][timestamps.length];
		for (int i = 0; i < data.size()-2; i++) {
			double[] timestamps_temp = convertDoubles(timestamps_str.get(i));
			double[] targetData_temp = convertDoubles(data.get(i));
			
			SplineInterpolator spline = new SplineInterpolator(); 
			PolynomialSplineFunction polynomical = spline.interpolate(timestamps_temp, targetData_temp);
			
			for (int j = 0; j < timestamps.length; j++) {
				otherData[i][j] = polynomical.value(timestamps[j]);
			}
		}
		
		ArrayList<Attribute> attributes;
		Instances dataSet;

		attributes = new ArrayList<Attribute>();
		
		for (String metric: metricName) {
			attributes.add(new Attribute(metric)); 
		}

		dataSet = new Instances("data", attributes, 0);
		
		for (int i = 0; i < timestamps.length; i++) {
		    double[] instanceValue1 = new double[dataSet.numAttributes()];
		    instanceValue1[0] = timestamps[i];
		    instanceValue1[1] = targetData[i];
		    
		    for (int j = 0; j < data.size()-2 ; j++) {
		    	instanceValue1[2+j] = otherData[j][i];
		    }

		    DenseInstance denseInstance1 = new DenseInstance(1.0, instanceValue1);
		    
		    dataSet.add(denseInstance1);
		}
				
		ArffSaver saver = new ArffSaver();
		saver.setInstances(dataSet);
		try {
			String workingDir = System.getProperty("user.dir");
			saver.setFile(new File(workingDir+"/"+fileName));
			saver.writeBatch();
		} catch (IOException e) {
			e.printStackTrace();
		}

		
	}
	
	public static double[] convertDoubles(ArrayList<String> strings)
	{
	    double[] ret = new double[strings.size()];
	    for (int i = 0; i < strings.size(); i++) {
	    	ret[i] = Double.valueOf(strings.get(i));
	    }
	    return ret;
	}
	
}
