package com.simon.imageloader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.simon.imageloader.imageloader.Util;

import java.util.ArrayList;

public class MainActivity extends Activity {
	private ListView lv;
	private ArrayList<CharSequence> arrayList;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		lv=(ListView) findViewById(R.id.lv);
		
	}

	public void select(View v){
		Intent intent=new Intent(this,ImagePickerActivity.class);
		startActivityForResult(intent, 10);
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		arrayList = data.getCharSequenceArrayListExtra("select_list");
		if(arrayList.size()!=0){
			lv.setAdapter(new MyAdapter());
		}
	}
	private class MyAdapter extends BaseAdapter{

		@Override
		public int getCount() {
			return arrayList.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ImageView iv=new ImageView(MainActivity.this);
			
			Util.getInstance().loadImage(arrayList.get(position).toString(), iv);
			return iv;
		}
		
	}
}
