import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    vus: 13500,
    duration: '30s',
};

export default function () {
    http.get('http://localhost:8089/test');
    sleep(1);
}