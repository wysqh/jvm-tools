/**
 * Copyright 2014 Alexey Ragozin
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
package samara;

import java.io.Serializable;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Thread stack sampler.
 *  
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class ThreadStackSampler {

	private static final ObjectName THREADING_MBEAN = name("java.lang:type=Threading");
	private static ObjectName name(String name) {
		try {
			return new ObjectName(name);
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}

	private ThreadMXBean threading;

	private Pattern threadFilter;
	private String rootElement;
	private Map<StackTraceElement, Integer> siteIndex = new HashMap<StackTraceElement, Integer>();
	private StackTraceElement[] sites = new StackTraceElement[0];
	private long[] threadSet;
	
	private List<Trace> traces = new ArrayList<Trace>();
	
	public ThreadStackSampler(MBeanServerConnection mserver) {
		this.threading = JMX.newMXBeanProxy(mserver, THREADING_MBEAN, ThreadMXBean.class);
	}
	
	public void setThreadFilter(String pattern) {
	    this.threadFilter = Pattern.compile(pattern);
	}

	public void setRootElement(String rootElement) {
	    this.rootElement = rootElement;
	}
	
	public void prime() {
        ThreadInfo[] ti = threading.dumpAllThreads(false, false);
        long[] tids = new long[ti.length];
        int n = 0;
        for(ThreadInfo t:ti) {
            long tid = t.getThreadId();
            String name = t.getThreadName();
            if (threadFilter == null || threadFilter.matcher(name).matches()) {
                tids[n++] = tid;
            }                
        }
        tids = Arrays.copyOf(tids, n);
        threadSet = tids;
	}
	
	public int getTraceCount() {
	    return traces.size();
	}
	
	public List<Trace> getTraces() {
	    if (sites.length != siteIndex.size()) {
	        sites = Arrays.copyOf(sites, siteIndex.size());
	    }
	    for(Trace t: traces) {
	        t.traceDictionary = sites;
	    }
	    return new ArrayList<Trace>(traces);
	}
	
	public void clearTraces() {
	    traces.clear();
	}
	
	public void collect() {

	    long timestamp = System.currentTimeMillis();
		ThreadInfo[] dump = threading.getThreadInfo(threadSet, Integer.MAX_VALUE);
		
		for(ThreadInfo ti: dump) {
		    Trace trace = newTrace(timestamp, ti);
		    if (trace !=  null) {
		        traces.add(trace);
		    }
		}
	}

	private Trace newTrace(long timestamp, ThreadInfo ti) {
	    StackTraceElement[] stackTrace = ti.getStackTrace();
        int[] stack = new int[stackTrace.length];
        boolean match = false;
	    for(int i = 0; i != stackTrace.length; ++i) {
	        stack[i] = toSiteID(stackTrace[i]);
	        if (rootElement.equals(stackTrace[i].toString())) {
	            match = true;	            
	            stack = Arrays.copyOf(stack, i + 1);
	            break;
	        }
	    }
	    
	    if (match) {
	        Trace trace = new Trace(ti.getThreadId(), timestamp, stack);
	        return trace;
	    }
	    else {
	        return null;
	    }
    }
	
	private int toSiteID(StackTraceElement e) {
	    Integer i = siteIndex.get(e);
	    if (i == null) {
	        i = siteIndex.size();
	        siteIndex.put(e, i);
	        if (sites.length <= i) {
	            sites = Arrays.copyOf(sites, 16 + (3 * sites.length / 2));
	        }
	        sites[i] = e;
	    }
	    return i;
	}

    @SuppressWarnings("serial")
    public static class Trace implements Serializable {
	    
	    private long threadId;
	    private long timestamp;
	    private int[] trace;
	    
	    private StackTraceElement[] traceDictionary;

        public Trace(long threadId, long timestamp, int[] trace) {
            this.threadId = threadId;
            this.timestamp = timestamp;
            this.trace = trace;
        }	
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public long getThreadId() {
            return threadId;
        }
        
        public StackTraceElement[] getTrace() {
            StackTraceElement[] strace = new StackTraceElement[trace.length];
            for(int i = 0; i != strace.length; ++i) {
                strace[i] = traceDictionary[trace[i]];
            }
            return strace;
        }
	}
}
