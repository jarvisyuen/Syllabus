package com.example.daidaijie.syllabusapplication;

import com.example.daidaijie.syllabusapplication.base.IBaseModel;
import com.example.daidaijie.syllabusapplication.bean.AuthLogin;
import com.example.daidaijie.syllabusapplication.bean.Semester;
import com.example.daidaijie.syllabusapplication.bean.UserLogin;
import com.example.daidaijie.syllabusapplication.retrofitApi.AuthLoginApi;
import com.example.daidaijie.syllabusapplication.util.LoggerUtil;

import org.joda.time.DateTime;

import io.realm.Realm;
import io.realm.RealmResults;
import retrofit2.Retrofit;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by daidaijie on 2016/10/13.
 */

public class LoginModel implements ILoginModel {

    UserLogin mUserLogin;

    AuthLogin mAuthLogin;

    Realm mRealm;

    Retrofit mRetrofit;

    Semester mCurrentSemester;

    AuthLoginApi authLoginApi;


    public LoginModel(Realm realm, Retrofit retrofit) {
        mRealm = realm;
        mRetrofit = retrofit;
    }

    @Override
    public void setUserLogin(UserLogin userLogin) {
        mUserLogin = userLogin;
    }

    @Override
    public void setAuthLogin(AuthLogin authLogin) {
        mAuthLogin = authLogin;
    }


    @Override
    public UserLogin getUserLogin() {
        if (mUserLogin != null) {
            return mUserLogin;
        } else {
            mRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    RealmResults<UserLogin> results = realm.where(UserLogin.class).findAll();
                    if (results.size() != 0) {
                        mUserLogin = realm.copyFromRealm(results.first());
                    }
                }
            });
        }
        return mUserLogin;
    }

    @Override
    public AuthLogin getAuthLogin() {
        if (mAuthLogin != null) {
            return mAuthLogin;
        } else {
            mRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    RealmResults<AuthLogin> results = realm.where(AuthLogin.class).findAll();
                    if (results.size() != 0) {
                        mAuthLogin = realm.copyFromRealm(results.first());
                    }
                }
            });
        }
        return mAuthLogin;
    }

    @Override
    public Observable<UserLogin> getUserLoginFromMemory() {
        return Observable.create(new Observable.OnSubscribe<UserLogin>() {
            @Override
            public void call(Subscriber<? super UserLogin> subscriber) {
                subscriber.onNext(mUserLogin);
                subscriber.onCompleted();
            }
        }).subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Observable<UserLogin> getUserLoginFromDisk() {
        return Observable.create(new Observable.OnSubscribe<UserLogin>() {
            @Override
            public void call(Subscriber<? super UserLogin> subscriber) {
                mRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        RealmResults<UserLogin> results = realm.where(UserLogin.class).findAll();
                        if (results.size() != 0) {
                            mUserLogin = realm.copyFromRealm(results.first());
                        }
                        LoggerUtil.e("userlogin", results.size() + "");

                    }
                });
                subscriber.onNext(mUserLogin);
                subscriber.onCompleted();
            }
        }).subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Observable<UserLogin> getUserLoginFromCache() {
//        return Observable.concat(getUserLoginFromMemory(), getUserLoginFromDisk())
        return getUserLoginFromDisk()
                .takeFirst(new Func1<UserLogin, Boolean>() {
                    @Override
                    public Boolean call(UserLogin userLogin) {
                        return userLogin != null;
                    }
                }).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void getUserLoginNormal(IBaseModel.OnGetSuccessCallBack<UserLogin> onGetSuccessCallBack) {
        if (mUserLogin != null) {
            onGetSuccessCallBack.onGetSuccess(mUserLogin);
            return;
        }
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                RealmResults<UserLogin> results = realm.where(UserLogin.class).findAll();
                if (results.size() != 0) {
                    mUserLogin = realm.copyFromRealm(results.first());
                }
            }
        });
        if (mUserLogin != null) {
            onGetSuccessCallBack.onGetSuccess(mUserLogin);
        }
    }

    /**
     * 更换新的登录接口
     * @return
     */
    @Override
    public Observable<AuthLogin> authLogin(String username, String password) {
        authLoginApi = mRetrofit.create(AuthLoginApi.class);
        return authLoginApi.authLogin(username, password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void saveUserLoginToDisk() {
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.where(UserLogin.class).findAll().deleteAllFromRealm();
                realm.copyToRealm(mUserLogin);
            }
        });
    }

    @Override
    public void saveAuthLoginToDisk() {
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.where(AuthLogin.class).findAll().deleteAllFromRealm();
                realm.copyToRealm(mAuthLogin);
            }
        });
    }

    @Override
    public void setCurrentSemester(final Semester currentSemester) {
        mCurrentSemester = currentSemester;
        mCurrentSemester.setCurrent(true);
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                /**
                 * 将旧的设置为非当前
                 */
                RealmResults<Semester> results = realm.where(Semester.class)
                        .equalTo("isCurrent", true).findAll();
                for (Semester semester : results) {
                    semester.setCurrent(false);
                }

                /**
                 * 再复制原有的并作为当前写入
                 */
                RealmResults<Semester> results2 = realm.where(Semester.class)
                        .equalTo("season", currentSemester.getSeason())
                        .equalTo("startYear", currentSemester.getStartYear()).findAll();
                if (results2.size() > 0) {
                    mCurrentSemester.setStartWeekTime(results2.first().getStartWeekTime());
                    results2.deleteAllFromRealm();
                }

                realm.copyToRealm(mCurrentSemester);
            }
        });
    }


    @Override
    public Semester getCurrentSemester() {
        if (mCurrentSemester != null) return mCurrentSemester;
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                RealmResults<Semester> results = mRealm.where(Semester.class)
                        .equalTo("isCurrent", true).findAll();
                if (results.size() != 0) {
                    mCurrentSemester = realm.copyFromRealm(results.first());
                }
            }
        });
        if (mCurrentSemester == null) {
            int queryYear;
            int querySem;
            DateTime dateTime = DateTime.now();
            int year = dateTime.getYear();
            int month = dateTime.getMonthOfYear();
            if (month > 1 && month < 8) {
                queryYear = year - 1;
                querySem = 2;
            } else if (month == 8) {
                queryYear = year;
                querySem = 3;
            } else {
                if (month > 8) {
                    queryYear = year;
                } else {
                    queryYear = year - 1;
                }
                querySem = 1;
            }
            mCurrentSemester = new Semester(queryYear, querySem);
            mCurrentSemester.setCurrent(true);
            mRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    realm.copyToRealm(mCurrentSemester);
                }
            });
        }
        return mCurrentSemester;
    }

    @Override
    public void updateSemester(final Semester semester) {
        mRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                RealmResults<Semester> results = realm.where(Semester.class)
                        .equalTo("season", semester.getSeason())
                        .equalTo("startYear", semester.getStartYear()).findAll();
                if (results.size() != 0) {
                    Semester realmSemester = results.first();
                    realmSemester.setStartWeekTime(semester.getStartWeekTime());
                }

            }
        });
    }


}
