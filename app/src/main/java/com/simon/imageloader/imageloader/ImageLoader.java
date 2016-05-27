package com.simon.imageloader.imageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
/**
 * 这个线程中的mPoolThreadHandler可能还没有初始化完成,但是在addTask到mTaskQueue后就要用到时,就会出现null painter exception
 * 解决这个可以使用java提供的Semaphore,并发一个计数信号量。从概念上讲，信号量维护了一个许可集。如有必要，在许可可用前会阻塞每一个 acquire()，
 * 然后再获取该许可。每个 release() 添加一个许可，从而可能释放一个正在阻塞的获取者。但是，不使用实际的许可对象，Semaphore 只对可用许可的号码进行计数，
 * 并采取相应的行动。拿到信号量的线程可以进入代码，否则就等待。通过acquire()和release()获取和释放访问许可。
 * 简单来讲就是acquire()消耗掉一个许可,而release()添加一个许可,当许可为0的时候,想要再次acquire()就会阻塞
 * public class SemaphoreTest {
 *
 *	     public static void main(String[] args) {
 *	        // 线程池
 *	        ExecutorService exec = Executors.newCachedThreadPool();
 *	        // 只能5个线程同时访问
 *	        final Semaphore semp = new Semaphore(5);
 *	        // 模拟20个客户端访问
 *	        for (int index = 0; index < 20; index++) {
 *	            final int NO = index;
 *	            Runnable run = new Runnable() {
 *	                public void run() {
 *	                    try {
 * 	                        // 获取许可
 *	                        semp.acquire();
 *                       System.out.println("Accessing: " + NO);
 *	                        Thread.sleep((long) (Math.random() * 10000));
 *	                        // 访问完后，释放 ，如果屏蔽下面的语句，则在控制台只能打印5条记录，之后线程一直阻塞
 *	                        semp.release();
 *	                    } catch (InterruptedException e) {
 *	                    }
 *	                }
 *	            };
 *	            exec.execute(run);
 *	        }
 *	        // 退出线程池
 *	        exec.shutdown();
 *	    }
 *	}
 */

/**
 * @author Administrator
 *	图片加载器,task任务列频繁调用,所以要做成单例模式
 */
public class ImageLoader {
	private static ImageLoader instance;
	/**
	 * 图片缓存的核心对象
	 */
	private LruCache<String, Bitmap> mLruCache;
	/**
	 * 线程池
	 */
	private ExecutorService mThreadPool;
	private static final int DEFAULT_THREAD_COUNT=1;
	/**
	 * 队列调度方式
	 */
	private static Type mType=Type.LIFO;
	/**
	 * 任务队列
	 */
	private LinkedList<Runnable> mTaskQueue;
	/**
	 * 后台轮询线程
	 */
	private Thread mPoolThread;
	private Handler mPoolThreadHandler;
	/**
	 *UI线程的Handler
	 */
	private Handler mUIHandler;
	//任务队列的
	public enum Type{
		FIFO,LIFO;
	}

	private ImageLoader(int threadCount,Type type){
		init(threadCount,type);
	}
	private Semaphore mSemaphorePoolThreadHandler=new Semaphore(0);
	private Semaphore mSemaphoreThreadPool;
	/**
	 * 构造方法中初始化一大堆成员变量
	 * @param threadCount
	 * @param type
	 */
	private void init(int threadCount,Type type) {
		//初始化后台轮询线程
		mPoolThread=new Thread(){
			@Override
			public void run() {
				Looper.prepare();//使用安卓异步消息机制处理mtaskQueue中的任务
				mPoolThreadHandler=new Handler(){
					@Override
					public void handleMessage(Message msg) {
						//从线程池取出一个任务执行
						mThreadPool.execute(getTask());

					}
				};
				mSemaphorePoolThreadHandler.release();//handler初始化完成,添加一个许可证
				Looper.loop();
			}
		};
		//启动线程池
		mPoolThread.start();
		mTaskQueue=new LinkedList<Runnable>();//任务队列
		mThreadPool=Executors.newFixedThreadPool(threadCount);//线程池管理者
		int maxSize=(int) Runtime.getRuntime().maxMemory();
		int cacheSize=maxSize/8;
		mLruCache=new LruCache<String, Bitmap>(cacheSize){

			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getRowBytes()*value.getHeight();
			}

		};
		mType=type;
		mSemaphoreThreadPool=new Semaphore(threadCount);
	}
	/**
	 * 从任务队列中根据Type的值取出任务
	 * @return
	 */
	public Runnable getTask(){
		try {
			//每取一个任务就消耗一个许可,消耗完成将阻塞,等待释放许可
			mSemaphoreThreadPool.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(mType==Type.FIFO){
			return mTaskQueue.removeFirst();
		}
		else if(mType==Type.LIFO){
			return mTaskQueue.removeLast();
		}
		return null;
	}
	/**
	 * 获取ImageLoader的单利方法
	 * @return
	 */
	public static ImageLoader getInstance(){
		//这里采用双重if判断,可以提高代码的执行效率
		//外层判断可能会有几个线程进到里面,然后线程同步加条件判断
		//保证单例唯一性
		if(instance==null){
			synchronized (ImageLoader.class) {
				if(instance==null){
					instance=new ImageLoader(DEFAULT_THREAD_COUNT,mType);
				}
			}
		}
		return instance;
	}

	/**
	 * 获取ImageLoader的单利方法的重载
	 * @return
	 */
	public static ImageLoader getInstance(int threadCount,Type type){
		//这里采用双重if判断,可以提高代码的执行效率
		//外层判断可能会有几个线程进到里面,然后线程同步加条件判断
		//保证单例唯一性
		if(instance==null){
			synchronized (ImageLoader.class) {
				if(instance==null){
					instance=new ImageLoader(threadCount,type);
				}
			}
		}
		return instance;
	}
	/**
	 * 图片加载的核心方法
	 * @param path
	 * @param imageView
	 */
	public void loadImage(final String path,final ImageView imageView){
		imageView.setTag(path);//带上标记,防止条目复用时出现乱跳现象
		mUIHandler=new Handler(){
			@Override
			public void handleMessage(Message msg) {
				ImageHolder holder=(ImageHolder) msg.obj;
				Bitmap bitmap=holder.mBitmap;
				ImageView imageView=holder.mImageView;
				String url=holder.path;
				if(imageView.getTag().toString().equals(url)){
					imageView.setImageBitmap(bitmap);
				}
			}
		};

		Bitmap mBitmap=getBitmapFromLruCache(path);

		if(mBitmap!=null){
			refreshHandler(path, imageView, mBitmap);
		}
		else{
			addTask(imageView,path);
		}

	}
	private void addTask(final ImageView imageView,final String path) {
		mTaskQueue.add(new Runnable() {

			@Override
			public void run() {
				//进行加载图片的逻辑
				//1.获取ImageView要显示的大小
				ImageSize imageSize = getImageSize(imageView);
				//2.开始压缩图片
				Bitmap mBitmap = compressBitmap(imageSize,path);
				//3.把图片添加到LruCache中去
				if(mLruCache.get(path)!=null){
					mLruCache.put(path, mBitmap);
				}
				//发送消息,让UiHandler把图片显示到imageview上去
				refreshHandler(path, imageView, mBitmap);
				//任务完成,可以释放许可,让后台轮询线程去处理下一个任务
				mSemaphoreThreadPool.release();
			}
		});
		try {
			if(mPoolThreadHandler==null){
				mSemaphorePoolThreadHandler.acquire();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//发送消息,让后台轮询线程去处理任务堆
		mPoolThreadHandler.sendEmptyMessage(0X110);

	}
	/**
	 * 把ImageView path Bitmap等信息发送到handler中去执行
	 * @param path
	 * @param imageView
	 * @param mBitmap
	 */
	private void refreshHandler(final String path, final ImageView imageView, Bitmap mBitmap) {
		Message message=Message.obtain();
		ImageHolder holder=new ImageHolder();
		holder.mBitmap=mBitmap;
		holder.mImageView=imageView;
		holder.path=path;
		message.obj=holder;
		mUIHandler.sendMessage(message);
	}
	/**
	 * 压缩图片
	 * @return
	 */
	private Bitmap compressBitmap(ImageSize imageSize,String path){
		BitmapFactory.Options option=new BitmapFactory.Options();
		option.inJustDecodeBounds=true;//只解析图片的宽高等信息,而不把图片加载到内存中去
		BitmapFactory.decodeFile(path, option);
		int outWidth = option.outWidth;
		int outHeight = option.outHeight;
		int width=imageSize.width;
		int height=imageSize.height;
		int inSimpleSize=1;
		if(outHeight>height||outWidth>height){
			int widthRadio=Math.round(outWidth*1.0f/width);
			int heightRadio=Math.round(outHeight*1.0f/height);
			inSimpleSize=Math.max(widthRadio, heightRadio);
		}
		option.inJustDecodeBounds=false;
		option.inSampleSize=inSimpleSize;

		return BitmapFactory.decodeFile(path, option);
	}
	/**
	 * 根据ImageView获取适当的压缩后的宽和高
	 * @param iv
	 * @return
	 */
	private ImageSize getImageSize(ImageView iv){
		LayoutParams lp = iv.getLayoutParams();
		//获取屏幕的矩阵
		DisplayMetrics dm = iv.getContext().getResources().getDisplayMetrics();
		int width=iv.getWidth();
		if(width<=0){
			width=lp.width;
		}
		if(width<=0){
			//使用反射获取ImageView的MaxWidth和MaxHeight兼容低版本
			width=getMaxValueFromInvoke(iv,"mMaxWidth");
		}
		if(width<=0){
			width=dm.widthPixels;
		}

		int height=iv.getHeight();
		if(height<=0){
			height=lp.width;
		}
		if(height<=0){
			height=getMaxValueFromInvoke(iv,"mMaxHeight");
		}
		if(height<=0){
			height=dm.heightPixels;
		}
		return new ImageSize(width, height);
	}
	/**
	 * 使用反射获取ImageView的MaxWidth和MaxHeight兼容低版本
	 * @param obj
	 * @param fieldName
	 * @return
	 */
	private int getMaxValueFromInvoke(Object obj,String fieldName){
		int value=0;
		try {
			Field field = ImageView.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			int fieldValue = field.getInt(obj);
			if(fieldValue>0&&fieldValue<Integer.MAX_VALUE){
				value=fieldValue;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return value;
	}
	/**
	 * 封装图片宽高的对象
	 * @author Administrator
	 *
	 */
	private class ImageSize{
		int width;
		int height;
		public ImageSize(int width,int height){
			this.width=width;
			this.height=height;
		}
	}
	//handler发送的msg.obj对象
	private class ImageHolder{
		Bitmap mBitmap;
		ImageView mImageView;
		String path;
	}
	//从缓存中去取图片
	private Bitmap getBitmapFromLruCache(String key) {

		return mLruCache.get(key);
	}

}
