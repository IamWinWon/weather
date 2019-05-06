package com.github.bkhezry.weather.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.github.bkhezry.weather.R;
import com.github.bkhezry.weather.model.CityInfo;
import com.github.bkhezry.weather.model.WeatherCollection;
import com.github.bkhezry.weather.model.currentweather.CurrentWeatherResponse;
import com.github.bkhezry.weather.model.daysweather.ListItem;
import com.github.bkhezry.weather.model.daysweather.MultipleDaysWeatherResponse;
import com.github.bkhezry.weather.model.fivedayweather.FiveDayResponse;
import com.github.bkhezry.weather.model.fivedayweather.ItemHourly;
import com.github.bkhezry.weather.service.ApiService;
import com.github.bkhezry.weather.ui.fragment.HourlyFragment;
import com.github.bkhezry.weather.ui.fragment.MultipleDaysFragment;
import com.github.bkhezry.weather.utils.ApiClient;
import com.github.bkhezry.weather.utils.AppUtil;
import com.github.bkhezry.weather.utils.Constants;
import com.github.pwittchen.prefser.library.rx2.Prefser;
import com.miguelcatalan.materialsearchview.MaterialSearchView;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.listeners.OnClickListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import butterknife.BindArray;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

public class MainActivity extends AppCompatActivity {

  @BindView(R.id.recycler_view)
  RecyclerView recyclerView;
  @BindView(R.id.temp_text_view)
  AppCompatTextView tempTextView;
  @BindView(R.id.description_text_view)
  AppCompatTextView descriptionTextView;
  @BindView(R.id.humidity_text_view)
  AppCompatTextView humidityTextView;
  @BindArray(R.array.mdcolor_500)
  @ColorInt
  int[] colors;
  @BindArray(R.array.mdcolor_500_alpha)
  @ColorInt
  int[] colorsAlpha;
  @BindView(R.id.animation_view)
  LottieAnimationView animationView;
  @BindView(R.id.toolbar)
  Toolbar toolbar;
  @BindView(R.id.search_view)
  MaterialSearchView searchView;
  @BindView(R.id.city_name_text_view)
  AppCompatTextView cityNameTextView;
  @BindView(R.id.wind_text_view)
  AppCompatTextView windTextView;
  private FastAdapter<WeatherCollection> mFastAdapter;
  private ItemAdapter<WeatherCollection> mItemAdapter;
  private CompositeDisposable disposable = new CompositeDisposable();
  private String defaultLang = "en";
  private List<WeatherCollection> weatherCollections;
  private ApiService apiService;
  private WeatherCollection todayWeatherCollection;
  private Prefser prefser;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    setSupportActionBar(toolbar);
    initSearchView();
    initValues();
    initRecyclerView();
    checkStoredCityInfo();
  }

  private void checkStoredCityInfo() {
    if (prefser.contains(Constants.CITY_INFO)) {
      CityInfo cityInfo = prefser.get(Constants.CITY_INFO, CityInfo.class, null);
      if (cityInfo != null) {
        requestWeather(cityInfo.getName());
        cityNameTextView.setText(String.format("%s, %s", cityInfo.getName(), cityInfo.getCountry()));
      }
    }
  }

  private void requestWeather(String cityName) {
    getCurrentWeather(cityName);
    getFiveDaysWeather(cityName);
  }

  private void initSearchView() {
    searchView.setVoiceSearch(false);
    searchView.setCursorDrawable(R.drawable.ic_action_action_search);
    searchView.setEllipsize(true);
    searchView.setOnQueryTextListener(new MaterialSearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        requestWeather(query);
        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return false;
      }
    });
  }

  private void initValues() {
    prefser = new Prefser(this);
    apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);
  }

  private void getCurrentWeather(String cityName) {
    disposable.add(
        apiService.getCurrentWeather(
            cityName, Constants.UNITS, defaultLang, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<CurrentWeatherResponse>() {
              @Override
              public void onSuccess(CurrentWeatherResponse currentWeatherResponse) {
                handleCurrentWeather(currentWeatherResponse);
                storeCityInfo(currentWeatherResponse);
              }

              @Override
              public void onError(Throwable e) {
                try {
                  HttpException error = (HttpException) e;
                  Log.e("MainActivity", "onError: " + e.getMessage());
                } catch (Exception exception) {

                }
              }
            })

    );
  }

  private void handleCurrentWeather(CurrentWeatherResponse response) {
    tempTextView.setText(String.format(Locale.getDefault(), "%.0f°", response.getMain().getTemp()));
    if (response.getWeather().size() != 0) {
      descriptionTextView.setText(response.getWeather().get(0).getMain());
      animationView.setAnimation(AppUtil.getWeatherAnimation(response.getWeather().get(0).getId()));
      animationView.playAnimation();
    }
    humidityTextView.setText(String.format(Locale.getDefault(), "%d%%", response.getMain().getHumidity()));
    windTextView.setText(String.format(Locale.getDefault(), "%.0fkm/hr", response.getWind().getSpeed()));
  }

  private void storeCityInfo(CurrentWeatherResponse response) {
    CityInfo cityInfo = new CityInfo();
    cityInfo.setCountry(response.getSys().getCountry());
    cityInfo.setId(response.getId());
    cityInfo.setName(response.getName());
    prefser.put(Constants.CITY_INFO, cityInfo);
    cityNameTextView.setText(String.format("%s, %s", cityInfo.getName(), cityInfo.getCountry()));
  }

  private void getFiveDaysWeather(String cityName) {
    disposable.add(
        apiService.getMultipleDaysWeather(
            cityName, Constants.UNITS, defaultLang, 5, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<MultipleDaysWeatherResponse>() {
              @Override
              public void onSuccess(MultipleDaysWeatherResponse response) {
                handleFiveDayResponse(response, cityName);
              }

              @Override
              public void onError(Throwable e) {
                try {
                  HttpException error = (HttpException) e;
                  Log.e("MainActivity", "onError: " + e.getMessage());
                } catch (Exception exception) {

                }
              }
            })
    );
  }

  private void handleFiveDayResponse(MultipleDaysWeatherResponse response, String cityName) {
    weatherCollections = new ArrayList<>();
    List<ListItem> list = response.getList();
    int day = 0;
    for (ListItem item : list) {
      Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
      Calendar newCalendar = AppUtil.addDays(calendar, day);
      WeatherCollection weatherCollection = new WeatherCollection();
      weatherCollection.setListItem(item);
      weatherCollection.setColor(colors[day]);
      weatherCollection.setColorAlpha(colorsAlpha[day]);
      weatherCollection.setTimestampStart(AppUtil.getStartOfDayTimestamp(newCalendar));
      weatherCollection.setTimestampEnd(AppUtil.getEndOfDayTimestamp(newCalendar));
      weatherCollections.add(weatherCollection);
      day++;
    }
    getFiveDaysHourlyWeather(cityName);
  }

  private void getFiveDaysHourlyWeather(String cityName) {
    disposable.add(
        apiService.getFiveDaysWeather(
            cityName, Constants.UNITS, defaultLang, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<FiveDayResponse>() {
              @Override
              public void onSuccess(FiveDayResponse response) {
                handleFiveDayHourlyResponse(response);
              }

              @Override
              public void onError(Throwable e) {
                Log.e("MainActivity", "onError: " + e.getMessage());
              }
            })

    );
  }

  private void handleFiveDayHourlyResponse(FiveDayResponse response) {
    for (WeatherCollection weatherCollection : weatherCollections) {
      ArrayList<ItemHourly> listItemHourlies = new ArrayList<>(response.getList());
      for (ItemHourly itemHourly : listItemHourlies) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(itemHourly.getDt() * 1000L);
        if (calendar.getTimeInMillis()
            <= weatherCollection.getTimestampEnd()
            && calendar.getTimeInMillis()
            > weatherCollection.getTimestampStart()) {
          weatherCollection.addListItemHourlies(itemHourly);
        }
      }
    }
    todayWeatherCollection = weatherCollections.remove(0);
    mItemAdapter.clear();
    mItemAdapter.add(weatherCollections);
  }


  private void initRecyclerView() {
    LinearLayoutManager layoutManager
        = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
    recyclerView.setLayoutManager(layoutManager);
    mItemAdapter = new ItemAdapter<>();
    mFastAdapter = FastAdapter.with(mItemAdapter);
    recyclerView.setItemAnimator(new DefaultItemAnimator());
    recyclerView.setAdapter(mFastAdapter);
    mFastAdapter.withOnClickListener(new OnClickListener<WeatherCollection>() {
      @Override
      public boolean onClick(@Nullable View v, @NonNull IAdapter<WeatherCollection> adapter, @NonNull WeatherCollection item, int position) {
        HourlyFragment hourlyFragment = new HourlyFragment();
        hourlyFragment.setWeatherCollection(item);
        AppUtil.showFragment(hourlyFragment, getSupportFragmentManager(), true);
        return true;
      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    disposable.dispose();
  }

  @OnClick(R.id.next_days_button)
  public void multipleDays() {
    AppUtil.showFragment(new MultipleDaysFragment(), getSupportFragmentManager(), true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    MenuItem item = menu.findItem(R.id.action_search);
    searchView.setMenuItem(item);
    return true;
  }
}
