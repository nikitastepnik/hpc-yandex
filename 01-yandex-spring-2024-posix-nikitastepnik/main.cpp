#include <cstdlib>
#include <csignal>
#include <fstream>
#include <iostream>
#include <pthread.h>
#include <queue>
#include <random>
#include <unistd.h>

std::sig_atomic_t terminate, is_signal_handled = 0;

pthread_mutex_t mtx;
pthread_cond_t consume_cv = PTHREAD_COND_INITIALIZER;

pthread_t producer_thread;
pthread_t interruptor_thread;
std::vector<pthread_t> consumer_threads;
std::sig_atomic_t active_consumers = 0;
bool is_all_consumers_started = false;

std::queue<int> numbers;
bool can_read_new_val = false;
int sum = 0;


void signal_handler(int) {
    terminate = 1;
    is_signal_handled = 1;
}

void *producer_routine(void *) {
    int number;
    std::ifstream ifs("in.txt");
    while (!is_all_consumers_started) {}
    while (ifs >> number && !terminate) {
        pthread_mutex_lock(&mtx);
        numbers.push(number);
        can_read_new_val = true;
        pthread_cond_signal(&consume_cv);
        pthread_mutex_unlock(&mtx);
    }
    while (!numbers.empty() && !terminate) {
        can_read_new_val = true;
        pthread_cond_signal(&consume_cv);
    }
    terminate = 1;
    while (active_consumers) {
        pthread_cond_broadcast(&consume_cv);
    }
    return nullptr;
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    int sleep_limit_ms = *reinterpret_cast<int *>(arg);
    int thread_sum = 0;
    std::random_device rd;
    std::mt19937 gen(rd());
    pthread_mutex_lock(&mtx);
    active_consumers++;
    pthread_mutex_unlock(&mtx);
    while (!terminate) {
        pthread_mutex_lock(&mtx);
        while (!can_read_new_val && !terminate) {
            pthread_cond_wait(&consume_cv, &mtx);
        }
        if (!numbers.empty() && !terminate) {
            int number = numbers.front();
            numbers.pop();
            can_read_new_val = false;
            thread_sum += number;
            pthread_mutex_unlock(&mtx);
            std::uniform_int_distribution<> dis(0, sleep_limit_ms);
            usleep(dis(gen));
        } else {
            pthread_mutex_unlock(&mtx);
        }
    }
    pthread_mutex_lock(&mtx);
    active_consumers--;
    pthread_mutex_unlock(&mtx);
    int *thread_sum_ptr = new int(thread_sum);
    return reinterpret_cast<void *>(thread_sum_ptr);
}

void *consumer_interruptor_routine(void *) {
    signal(SIGTERM, signal_handler);
    while (!terminate) {
        int id = rand() % consumer_threads.size();
        pthread_cancel(consumer_threads[id]);
    }
    if (is_signal_handled) {
        pthread_cancel(producer_thread);
        pthread_cond_broadcast(&consume_cv);
    }
    return nullptr;
}

int run_threads(int consumers_count, int sleep_limit_ms) {
    consumer_threads.resize(consumers_count);
    for (int i = 0; i < consumers_count; ++i) {
        pthread_create(&consumer_threads[i], nullptr, consumer_routine, &sleep_limit_ms);
    }
    pthread_create(&producer_thread, nullptr, producer_routine, nullptr);
    pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, nullptr);
    while (active_consumers != consumers_count) {}
    is_all_consumers_started = true;
    void *result;

    pthread_join(producer_thread, &result);
    pthread_join(interruptor_thread, &result);
    for (auto &thread: consumer_threads) {
        pthread_join(thread, &result);
        int *result_ptr = reinterpret_cast<int *>(result);
        if (result_ptr) {
            sum += *result_ptr;
            delete reinterpret_cast<int *>(result);
        }
    }
    return sum;
}

int main(int, char *argv[]) {
    pthread_mutex_init(&mtx, nullptr);
    int consumers_count = std::stoi(argv[1]);
    int sleep_limit_ms = std::stoi(argv[2]);
    pthread_mutex_init(&mtx, nullptr);
    std::cout << run_threads(consumers_count, sleep_limit_ms) << std::endl;
    pthread_mutex_destroy(&mtx);
    return 0;
}
