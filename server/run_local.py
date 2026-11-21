"""Run the existing FastAPI app with local TLS. No database/OPAQUE keys are replaced.
Use the project's .venv. The debug APK trusts only the generated public CA, not arbitrary certs.
"""
import argparse
import datetime as dt
import ipaddress
import os
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID

ROOT = Path(__file__).resolve().parents[1]


def prepare(hosts):
    directory = ROOT / '.runtime/local-tls'
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    ca_key_path, ca_path = directory/'ca-key.pem', directory/'ca.pem'
    now = dt.datetime.now(dt.timezone.utc)
    if ca_key_path.exists() != ca_path.exists():
        raise RuntimeError('Incomplete local CA. Restore the matching pair; existing keys are not overwritten.')
    if ca_key_path.exists():
        ca_key = serialization.load_pem_private_key(ca_key_path.read_bytes(), password=None)
        ca = x509.load_pem_x509_certificate(ca_path.read_bytes())
        if ca_key.public_key().public_numbers() != ca.public_key().public_numbers():
            raise RuntimeError('Local CA certificate/key mismatch')
    else:
        ca_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Secure Calendar local development CA')])
        ca = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(ca_key.public_key())
              .serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=5))
              .not_valid_after(now+dt.timedelta(days=3650))
              .add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True)
              .add_extension(x509.KeyUsage(False,False,False,False,False,True,True,False,False),critical=True)
              .sign(ca_key,hashes.SHA256()))
        private_write(ca_key_path, ca_key)
        ca_path.write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    if ca.not_valid_after_utc <= now:
        raise RuntimeError('Local CA expired; generate a new CA deliberately and rebuild debug APK')
    names=[]
    for host in sorted(set(['localhost','127.0.0.1','10.0.2.2']+hosts)):
        try:names.append(x509.IPAddress(ipaddress.ip_address(host)))
        except ValueError:names.append(x509.DNSName(host))
    key = rsa.generate_private_key(public_exponent=65537,key_size=2048)
    leaf = (x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Secure Calendar local server')]))
            .issuer_name(ca.subject).public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now-dt.timedelta(minutes=5)).not_valid_after(min(now+dt.timedelta(days=365),ca.not_valid_after_utc))
            .add_extension(x509.SubjectAlternativeName(names),critical=False)
            .add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True)
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False)
            .sign(ca_key,hashes.SHA256()))
    private_write(directory/'server-key.pem',key)
    (directory/'server.pem').write_bytes(leaf.public_bytes(serialization.Encoding.PEM)+ca.public_bytes(serialization.Encoding.PEM))
    resource = ROOT/'app/src/debug/res/raw/local_server_ca.pem'
    resource.parent.mkdir(parents=True,exist_ok=True)
    public = ca.public_bytes(serialization.Encoding.PEM)
    changed = not resource.exists() or resource.read_bytes()!=public
    resource.write_bytes(public)
    print('Local certificate hosts:', ', '.join(str(n.value) for n in names))
    if changed:print('Debug CA updated: rebuild and install debug APK before connecting.')
    print('TLS keys stay in .runtime/local-tls; existing database and OPAQUE setup are unchanged.')
    return directory


def private_write(path,key):
    # Create private files with restricted permissions from the first write.
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    os.chmod(path,0o600)
    with os.fdopen(fd,'wb') as out:
        out.write(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lan-host',action='append',default=[],help='Actual LAN IP or hostname; repeat for multiple interfaces')
    parser.add_argument('--port',type=int,default=8443)
    parser.add_argument('--prepare-only',action='store_true')
    args=parser.parse_args()
    directory=prepare(args.lan_host)
    if not args.prepare_only:
        import uvicorn
        uvicorn.run('server.main:app',host='0.0.0.0',port=args.port,
                    ssl_certfile=str(directory/'server.pem'),ssl_keyfile=str(directory/'server-key.pem'),access_log=False)

if __name__=='__main__':main()
