package com.simon.imageloader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;

import com.simon.imageloader.imageloader.ImageLoader;
import com.simon.imageloader.imageloader.domain.PathBean;

import java.util.ArrayList;
import java.util.List;

public class ImagePickerActivity extends Activity implements OnItemClickListener {
	private List<PathBean> images;
	private GridView gv;
			@Override
			protected void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);
				setContentView(R.layout.activity_image_picker);
				intent = getIntent();
				gv = (GridView) findViewById(R.id.gv);
				gv.setOnItemClickListener(this);
				fillData();
			}
			public void ok(View v){
				intent.putCharSequenceArrayListExtra("select_list", select_image);
				setResult(99, intent);
				finish();
			}

			private Handler handler=new Handler(){
				public void handleMessage(android.os.Message msg) {
					gv.setAdapter(new MyAdapter());
				};
			};
			private Intent intent;
			private class MyAdapter extends BaseAdapter{

				@Override
				public int getCount() {
					return images.size();
				}

				@Override
				public Object getItem(int position) {
					// TODO Auto-generated method stub
					return null;
				}

				@Override
				public long getItemId(int position) {
					// TODO Auto-generated method stub
					return position;
				}

				@Override
				public View getView(int position, View convertView, ViewGroup parent) {
					ViewHolder vh;
					if(convertView==null){
						convertView=LayoutInflater.from(ImagePickerActivity.this).inflate(R.layout.gridview_select_item, null);
						vh=new ViewHolder(convertView);
					}
					vh=(ViewHolder) convertView.getTag();
					vh.iv.setImageResource(R.drawable.pictures_no);
					ImageLoader instance = ImageLoader.getInstance(5, ImageLoader.Type.LIFO);
					instance.loadImage(images.get(position).path, vh.iv);
					vh.cb.setChecked(images.get(position).isChecked);
					return convertView;
				}
				
			}
			
			private class ViewHolder{
				ImageView iv;
				CheckBox cb;
				public ViewHolder(View view){
					iv=(ImageView) view.findViewById(R.id.iv);
					cb=(CheckBox) view.findViewById(R.id.cb);
					view.setTag(this);
				}
				
			}
			
			private void fillData() {
				images=new ArrayList<PathBean>();
				final Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				new Thread(){
					public void run() {
						Cursor cursor = getContentResolver().query(mImageUri, null, MediaStore.Images.Media.MIME_TYPE + "=? or "
								+ MediaStore.Images.Media.MIME_TYPE + "=?", new String[] { "image/jpeg", "image/png" }, 
								MediaStore.Images.Media.DATE_MODIFIED);
						if(cursor!=null){
							for(;cursor.moveToNext();){
								PathBean bean=new PathBean();
								// ��ȡͼƬ��·��
								String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
								Log.e("ͼƬ��·��", path);
								bean.path=path;
								images.add(bean);
							}
						}
						cursor.close();
						handler.sendEmptyMessage(0X119);
						
					};
				}.start();
				
			}
			private ArrayList<CharSequence> select_image=new ArrayList<CharSequence>();
			//������Ŀ����¼�
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ViewHolder vh=(ViewHolder) view.getTag();
				vh.cb.setChecked(!vh.cb.isChecked());
				String path=images.get(position).path;
				images.get(position).isChecked=vh.cb.isChecked();
				if(vh.cb.isChecked()){
					if(select_image.contains(path))
						return;
					select_image.add(path);
				}
				else{
					if(select_image.contains(path))
						select_image.remove(path);
					return;
				}
			}
}
